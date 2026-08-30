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

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;

    protected LlmUsageLogEntity() {
    }

    public LlmUsageLogEntity(
            String id,
            String traceId,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            String outputText
    ) {
        this.id = id;
        this.traceId = traceId;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.outputText = outputText;
    }

    public LlmUsageLogEntity(
            String id,
            String traceId,
            String model,
            Integer promptTokens,
            Integer completionTokens
    ) {
        this(id, traceId, model, promptTokens, completionTokens, null);
    }

    public String getId() {
        return id;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getModel() {
        return model;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public String getOutputText() {
        return outputText;
    }

    public void setOutputText(String outputText) {
        this.outputText = outputText;
    }
}
