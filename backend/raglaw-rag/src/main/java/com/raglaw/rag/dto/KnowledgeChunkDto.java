package com.raglaw.rag.dto;

public record KnowledgeChunkDto(
        String id,
        int chunkIndex,
        String content,
        String l1Path,
        String l2Path,
        String l3Path,
        String chunkLevel
) {
}
