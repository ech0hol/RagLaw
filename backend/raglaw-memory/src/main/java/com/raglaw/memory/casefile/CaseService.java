package com.raglaw.memory.casefile;

import com.raglaw.common.util.Ids;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseService {
    private final CaseRepository repository;
    private final Clock clock;

    @Autowired
    public CaseService(CaseRepository repository) {
        this(repository, Clock.systemUTC());
    }

    CaseService(CaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public CaseEntity create(String tenantId, String userId, String title) {
        CaseScope scope = new CaseScope(tenantId, userId, Ids.newId());
        return repository.save(new CaseEntity(scope.caseId(), scope, title, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Optional<CaseEntity> get(String tenantId, String userId, String caseId) {
        return repository.findByIdAndTenantIdAndUserId(caseId, tenantId, userId);
    }

    @Transactional(readOnly = true)
    public List<CaseEntity> list(String tenantId, String userId) {
        return repository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId);
    }

    @Transactional(readOnly = true)
    public boolean existsOwnedBy(String tenantId, String userId, String caseId) {
        return caseId != null && repository.findByIdAndTenantIdAndUserId(caseId, tenantId, userId).isPresent();
    }
}
