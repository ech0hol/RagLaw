package com.raglaw.agentscope.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLogEntity, String> {
}
