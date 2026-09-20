package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentResolutionObservationRepository extends JpaRepository<AgentResolutionObservationEntity, String> {
    List<AgentResolutionObservationEntity> findByTraceId(String traceId);
}
