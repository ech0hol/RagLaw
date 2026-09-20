package com.raglaw.memory.service;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.domain.CaseMemoryVersionEntity;
import com.raglaw.memory.domain.CaseMemoryVersionRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultMemorySnapshotService implements MemorySnapshotService {
    private final CaseMemoryVersionRepository versionRepository;
    private final Clock clock;

    @Autowired
    public DefaultMemorySnapshotService(CaseMemoryVersionRepository versionRepository) {
        this(versionRepository, Clock.systemUTC());
    }

    DefaultMemorySnapshotService(CaseMemoryVersionRepository versionRepository, Clock clock) {
        this.versionRepository = versionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CaseMemorySnapshot freeze(CaseScope scope) {
        long version = versionRepository.findByTenantIdAndUserIdAndCaseId(
                        scope.tenantId(), scope.userId(), scope.caseId())
                .map(CaseMemoryVersionEntity::getLatestSequence)
                .orElse(0L);
        return new CaseMemorySnapshot(scope, version, Instant.now(clock));
    }
}
