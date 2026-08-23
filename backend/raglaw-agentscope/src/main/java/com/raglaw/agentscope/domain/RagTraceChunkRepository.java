package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RagTraceChunkRepository extends JpaRepository<RagTraceChunkEntity, String> {

    List<RagTraceChunkEntity> findByTraceIdOrderByScoreDesc(String traceId);
}
