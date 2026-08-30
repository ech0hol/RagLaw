package com.raglaw.rag.dto;

import java.time.Instant;

public record KnowledgeHitDto(
        String chunkId,
        String documentId,
        String title,
        String path,
        String excerpt,
        double score,
        Instant createdAt,
        String effectiveDate
) {
}
