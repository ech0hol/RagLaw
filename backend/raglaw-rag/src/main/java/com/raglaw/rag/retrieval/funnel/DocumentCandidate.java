package com.raglaw.rag.retrieval.funnel;

public record DocumentCandidate(
        String documentId,
        String title,
        String categoryPath,
        double score
) {
}
