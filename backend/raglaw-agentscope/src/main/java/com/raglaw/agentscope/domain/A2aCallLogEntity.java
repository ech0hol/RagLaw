package com.raglaw.agentscope.domain;

import com.raglaw.common.jpa.ColumnLengths;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raglaw_a2a_call_log")
public class A2aCallLogEntity {

    @Id
    @Column(length = ColumnLengths.UUID)
    private String id;

    @Column(name = "trace_id", length = ColumnLengths.UUID, nullable = false)
    private String traceId;

    @Column(name = "from_agent", length = ColumnLengths.AGENT_CODE, nullable = false)
    private String fromAgent;

    @Column(name = "to_agent", length = ColumnLengths.AGENT_CODE, nullable = false)
    private String toAgent;

    @Column(name = "input_summary")
    private String inputSummary;

    @Column(name = "output_summary")
    private String outputSummary;

    @Column(name = "latency_ms")
    private Long latencyMs;

    protected A2aCallLogEntity() {
    }

    public A2aCallLogEntity(
            String id,
            String traceId,
            String fromAgent,
            String toAgent,
            String inputSummary,
            String outputSummary,
            Long latencyMs
    ) {
        this.id = id;
        this.traceId = traceId;
        this.fromAgent = fromAgent;
        this.toAgent = toAgent;
        this.inputSummary = inputSummary;
        this.outputSummary = outputSummary;
        this.latencyMs = latencyMs;
    }

    public String getFromAgent() {
        return fromAgent;
    }

    public String getToAgent() {
        return toAgent;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }
}
