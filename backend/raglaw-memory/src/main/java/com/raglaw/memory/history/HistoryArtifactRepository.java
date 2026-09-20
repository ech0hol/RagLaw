package com.raglaw.memory.history;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryArtifactRepository extends JpaRepository<HistoryArtifactEntity, String> {
    Optional<HistoryArtifactEntity> findByIdAndTenantIdAndUserIdAndCaseId(String id, String tenantId, String userId, String caseId);
}
