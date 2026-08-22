package com.raglaw.agentscope.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raglaw_rag_trace_stage")
public class RagTraceStageEntity {

    @Id
    private String id;

    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Column(nullable = false)
    private String stage;

    @Column(name = "detail_json")
    private String detailJson;

    @Column(name = "duration_ms")
    private Long durationMs;

    protected RagTraceStageEntity() {
    }

    public RagTraceStageEntity(String id, String traceId, String stage, String detailJson, Long durationMs) {
        this.id = id;
        this.traceId = traceId;
        this.stage = stage;
        this.detailJson = detailJson;
        this.durationMs = durationMs;
    }

    public String getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getStage() {
        return stage;
    }
}
