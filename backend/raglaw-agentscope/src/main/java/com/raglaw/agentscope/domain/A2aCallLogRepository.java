package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface A2aCallLogRepository extends JpaRepository<A2aCallLogEntity, String> {

    List<A2aCallLogEntity> findByTraceId(String traceId);
}
