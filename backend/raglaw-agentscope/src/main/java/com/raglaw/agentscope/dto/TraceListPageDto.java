package com.raglaw.agentscope.dto;

import java.util.List;

public record TraceListPageDto(
        List<TraceSummaryDto> items,
        int page,
        int pageSize,
        long total
) {
}
