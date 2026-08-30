package com.raglaw.agentscope.dto;

public record TraceA2aCallDto(
        String fromAgent,
        String toAgent,
        String inputSummary,
        String outputSummary,
        Long latencyMs
) {
}
