package com.raglaw.memory.service;

import com.raglaw.memory.domain.CaseMemoryEntity;
import com.raglaw.memory.domain.CaseMemoryRepository;
import com.raglaw.memory.domain.VerificationStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCaseMemoryQueryService implements CaseMemoryQueryService {
    private static final Map<String, Set<String>> TASK_PREDICATES = Map.of(
            "CONTRACT_REVIEW", Set.of("PARTY", "CONTRACT_TYPE", "CONTRACT_DATE", "TERM", "OBLIGATION", "LIABILITY", "TERMINATION"),
            "LABOR_DISPUTE", Set.of("EMPLOYMENT_STATUS", "MONTHLY_SALARY", "EMPLOYMENT_START", "EMPLOYMENT_END", "DISPUTE_FACT", "EVIDENCE"),
            "LEGAL_CONSULTATION", Set.of("LEGAL_ISSUE", "PARTY", "DATE", "AMOUNT", "EVIDENCE")
    );

    private final CaseMemoryRepository repository;

    public DefaultCaseMemoryQueryService(CaseMemoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CaseMemoryEntity> findForContext(ContextRequest request) {
        var snapshot = request.snapshot();
        Set<String> predicates = request.requiredPredicates().isEmpty()
                ? TASK_PREDICATES.getOrDefault(request.taskType().toUpperCase(Locale.ROOT), Set.of())
                : request.requiredPredicates().stream().map(value -> value.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        return repository.findByTenantIdAndUserIdAndCaseIdAndCaseSequenceLessThanEqualAndLifecycleOrderByCaseSequenceAsc(
                        snapshot.scope().tenantId(), snapshot.scope().userId(), snapshot.scope().caseId(),
                        snapshot.version(), com.raglaw.memory.domain.MemoryLifecycle.ACTIVE)
                .stream()
                .filter(memory -> predicates.isEmpty() || predicates.contains(memory.getPredicate().toUpperCase(Locale.ROOT)))
                .sorted(Comparator.comparingInt(DefaultCaseMemoryQueryService::verificationPriority).reversed()
                        .thenComparing(CaseMemoryEntity::getCaseSequence, Comparator.reverseOrder()))
                .toList();
    }

    private static int verificationPriority(CaseMemoryEntity memory) {
        return switch (memory.getVerification()) {
            case HUMAN_VERIFIED -> 500;
            case USER_CONFIRMED -> 400;
            case CORROBORATED -> 300;
            case DISPUTED -> 250;
            case UNVERIFIED -> 100;
        };
    }
}
