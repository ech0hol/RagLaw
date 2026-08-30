package com.raglaw.agentscope.dto;

public record TraceLlmUsageDto(
        String model,
        Integer promptTokens,
        Integer completionTokens,
        String outputText
) {
}
