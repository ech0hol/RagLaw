package com.raglaw.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.util.Ids;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.domain.CaseMemoryEntity;
import com.raglaw.memory.domain.CaseMemoryRepository;
import com.raglaw.memory.domain.CaseMemoryVersionEntity;
import com.raglaw.memory.domain.CaseMemoryVersionRepository;
import com.raglaw.memory.domain.MemoryAction;
import com.raglaw.memory.domain.MemoryAuditEntity;
import com.raglaw.memory.domain.MemoryAuditRepository;
import com.raglaw.memory.domain.MemoryLifecycle;
import com.raglaw.memory.domain.VerificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseMemoryCommandService {
    private final CaseMemoryRepository memoryRepository;
    private final MemoryAuditRepository auditRepository;
    private final CaseMemoryVersionRepository versionRepository;
    private final MemoryAdmissionPolicy admissionPolicy;
    private final MemoryConflictResolver conflictResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CaseMemoryCommandService(CaseMemoryRepository memoryRepository,
                                    MemoryAuditRepository auditRepository,
                                    CaseMemoryVersionRepository versionRepository,
                                    MemoryAdmissionPolicy admissionPolicy,
                                    MemoryConflictResolver conflictResolver,
                                    ObjectMapper objectMapper) {
        this(memoryRepository, auditRepository, versionRepository, admissionPolicy, conflictResolver,
                objectMapper, Clock.systemUTC());
    }

    CaseMemoryCommandService(CaseMemoryRepository memoryRepository,
                             MemoryAuditRepository auditRepository,
                             CaseMemoryVersionRepository versionRepository,
                             MemoryAdmissionPolicy admissionPolicy,
                             MemoryConflictResolver conflictResolver,
                             ObjectMapper objectMapper,
                             Clock clock) {
        this.memoryRepository = memoryRepository;
        this.auditRepository = auditRepository;
        this.versionRepository = versionRepository;
        this.admissionPolicy = admissionPolicy;
        this.conflictResolver = conflictResolver;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public MemoryCommandResult apply(CaseScope scope, MemoryCandidate candidate, String actor) {
        AdmissionDecision admission = admissionPolicy.evaluate(candidate);
        if (!admission.accepted()) return MemoryCommandResult.rejected(admission.reason());

        Instant now = Instant.now(clock);
        CaseMemoryVersionEntity version = versionRepository
                .findByTenantIdAndUserIdAndCaseId(scope.tenantId(), scope.userId(), scope.caseId())
                .orElseGet(() -> versionRepository.save(new CaseMemoryVersionEntity(scope, now)));
        List<CaseMemoryEntity> existing = memoryRepository.active(scope);
        MemoryResolution resolution = conflictResolver.resolve(candidate, existing);
        if (resolution.action() == MemoryAction.NO_OP) {
            writeAudit(scope, candidate, resolution, actor, now, resolution.affectedMemoryIds(), List.of());
            return new MemoryCommandResult(resolution, null, version.getLatestSequence(), true);
        }

        long sequence = version.nextSequence(now);
        versionRepository.save(version);
        String newId = Ids.newId();
        MemoryLifecycle lifecycle = MemoryLifecycle.ACTIVE;
        VerificationStatus verification = resolution.verificationStatus();
        CaseMemoryEntity created = new CaseMemoryEntity(newId, scope, candidate.subjectType(), candidate.subjectId(),
                candidate.predicate(), candidate.valueJson(), lifecycle, verification, candidate.sourceType(),
                candidate.sourceId(), candidate.validFrom(), candidate.validTo(),
                resolution.action() == MemoryAction.SUPERSEDE && !resolution.affectedMemoryIds().isEmpty()
                        ? resolution.affectedMemoryIds().get(0) : null,
                null, sequence, now);
        memoryRepository.save(created);

        for (CaseMemoryEntity old : existing) {
            if (!resolution.affectedMemoryIds().contains(old.getId())) continue;
            switch (resolution.action()) {
                case SUPERSEDE -> old.supersedeBy(newId, now);
                case MARK_DISPUTED -> old.markDisputed(now);
                case INVALIDATE -> old.invalidate(now);
                default -> { }
            }
            memoryRepository.save(old);
        }
        writeAudit(scope, candidate, resolution, actor, now, resolution.affectedMemoryIds(), List.of(newId));
        return new MemoryCommandResult(resolution, newId, sequence, true);
    }

    private void writeAudit(CaseScope scope, MemoryCandidate candidate, MemoryResolution resolution,
                            String actor, Instant now, List<String> oldIds, List<String> newIds) {
        auditRepository.save(new MemoryAuditEntity(
                Ids.newId(), scope, serialize(candidate), resolution.action(), resolution.action(),
                resolution.reason(), actor == null || actor.isBlank() ? "system" : actor,
                serialize(oldIds), serialize(newIds), now));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize memory audit", exception);
        }
    }
}
