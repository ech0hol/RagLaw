package com.raglaw.agentscope.dto;

import java.util.List;

public record TraceDetailDto(
        TraceSummaryDto trace,
        List<TraceStageDto> stages,
        List<TraceChunkDto> chunks
) {
}
