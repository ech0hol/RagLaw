package com.raglaw.agentscope.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShadowRouteLogRepository extends JpaRepository<ShadowRouteLogEntity, String> {

    List<ShadowRouteLogEntity> findByTraceIdOrderByCreatedAtAsc(String traceId);

    List<ShadowRouteLogEntity> findByCreatedAtAfterOrderByCreatedAtDesc(Instant createdAt);

    void deleteByTraceId(String traceId);

    void deleteByTraceIdIn(Collection<String> traceIds);
}
