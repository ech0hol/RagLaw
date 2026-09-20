package com.raglaw.memory.casefile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseRepository extends JpaRepository<CaseEntity, String> {
    Optional<CaseEntity> findByIdAndTenantIdAndUserId(String id, String tenantId, String userId);
    List<CaseEntity> findByTenantIdAndUserIdOrderByUpdatedAtDesc(String tenantId, String userId);
}
