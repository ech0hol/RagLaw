package com.raglaw.memory.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryAuditRepository extends JpaRepository<MemoryAuditEntity, String> {
}
