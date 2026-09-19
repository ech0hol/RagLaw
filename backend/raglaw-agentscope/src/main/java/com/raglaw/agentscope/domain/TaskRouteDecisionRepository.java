package com.raglaw.agentscope.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRouteDecisionRepository extends JpaRepository<TaskRouteDecisionEntity, String> {
    List<TaskRouteDecisionEntity> findByTraceIdOrderByCreatedAtAsc(String traceId);
    List<TaskRouteDecisionEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);
    List<TaskRouteDecisionEntity> findByRiskLevelOrderByCreatedAtDesc(String riskLevel);
    List<TaskRouteDecisionEntity> findByCandidateTaskTypeOrderByCreatedAtDesc(String candidateTaskType);
}
