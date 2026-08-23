package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagTraceStageRepository extends JpaRepository<RagTraceStageEntity, String> {

    List<RagTraceStageEntity> findByTraceId(String traceId);
}
