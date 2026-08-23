package com.raglaw.rag.dto;

public record KnowledgeHitDto(
        String chunkId,
        String documentId,
        String title,
        String path,
        String excerpt,
        double score
) {
}
