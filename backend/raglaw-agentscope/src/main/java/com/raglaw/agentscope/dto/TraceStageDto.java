package com.raglaw.agentscope.dto;

public record TraceStageDto(
        String id,
        String stage,
        String detailJson,
        Long durationMs
) {
}
