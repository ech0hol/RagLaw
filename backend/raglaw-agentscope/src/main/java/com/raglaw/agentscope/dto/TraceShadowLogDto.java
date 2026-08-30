package com.raglaw.agentscope.dto;

import java.time.Instant;

public record TraceShadowLogDto(
        String id,
        String shadowType,
        String userValue,
        String systemValue,
        Boolean hit,
        Integer rank,
        String confidenceJson,
        Instant createdAt
) {
}
