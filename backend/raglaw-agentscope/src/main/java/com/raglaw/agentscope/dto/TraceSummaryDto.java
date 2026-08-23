package com.raglaw.agentscope.dto;

import java.time.Instant;

public record TraceSummaryDto(
        String id,
        String conversationId,
        String agentCode,
        String queryText,
        Long latencyMs,
        Instant createdAt
) {
}
