package com.raglaw.rag.retrieval.funnel;

import java.util.List;

public record FunnelResult(
        List<String> scopePaths,
        List<String> topicPaths,
        List<String> lockedDocumentIds,
        double topicConfidence,
        double documentConfidence,
        String degradationLevel,
        String scopeRouteReason,
        boolean lowConfidence,
        List<DocumentCandidate> documentRanking
) {
    public static FunnelResult pinned(
            List<String> scopePaths,
            String documentId,
            String scopeRouteReason,
            boolean lowConfidence
    ) {
        return new FunnelResult(
                scopePaths,
                List.of(),
                List.of(documentId),
                1.0,
                1.0,
                "PINNED",
                scopeRouteReason,
                lowConfidence,
                List.of()
        );
    }
}
