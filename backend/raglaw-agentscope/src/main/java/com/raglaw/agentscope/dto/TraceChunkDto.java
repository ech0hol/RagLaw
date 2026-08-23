package com.raglaw.agentscope.dto;

public record TraceChunkDto(
        String id,
        String chunkId,
        Double score,
        String path,
        String excerpt
) {
}
