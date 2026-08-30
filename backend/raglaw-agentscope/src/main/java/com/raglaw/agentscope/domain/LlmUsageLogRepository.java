package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLogEntity, String> {

    List<LlmUsageLogEntity> findByTraceId(String traceId);
}
