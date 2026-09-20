package com.raglaw.memory.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseMemoryVersionRepository extends JpaRepository<CaseMemoryVersionEntity, CaseMemoryVersionId> {
    Optional<CaseMemoryVersionEntity> findByTenantIdAndUserIdAndCaseId(String tenantId, String userId, String caseId);
}
