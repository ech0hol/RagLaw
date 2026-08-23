package com.raglaw.agentscope.domain;

import com.raglaw.common.jpa.ColumnLengths;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raglaw_llm_usage_log")
public class LlmUsageLogEntity {

    @Id
    @Column(length = ColumnLengths.UUID)
    private String id;

    @Column(name = "trace_id", nullable = false, length = ColumnLengths.UUID)
    private String traceId;

    @Column(nullable = false, length = ColumnLengths.MODEL)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    protected LlmUsageLogEntity() {
    }

    public LlmUsageLogEntity(
            String id,
            String traceId,
            String model,
            Integer promptTokens,
            Integer completionTokens
    ) {
        this.id = id;
        this.traceId = traceId;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }
}
