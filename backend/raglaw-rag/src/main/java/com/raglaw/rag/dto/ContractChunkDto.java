package com.raglaw.rag.dto;

public record ContractChunkDto(
        String id,
        int chunkIndex,
        String content,
        String chunkLevel
) {
}
