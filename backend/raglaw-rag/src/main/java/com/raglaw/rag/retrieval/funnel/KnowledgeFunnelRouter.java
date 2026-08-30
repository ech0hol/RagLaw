package com.raglaw.rag.retrieval.funnel;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeFunnelRouter {

    private final TopicRanker topicRanker;
    private final DocumentRanker documentRanker;
    private final FunnelDegradationPolicy degradationPolicy;

    public KnowledgeFunnelRouter(
            TopicRanker topicRanker,
            DocumentRanker documentRanker,
            FunnelDegradationPolicy degradationPolicy
    ) {
        this.topicRanker = topicRanker;
        this.documentRanker = documentRanker;
        this.degradationPolicy = degradationPolicy;
    }

    public FunnelDegradationPolicy.AppliedFunnel route(
            String query,
            List<String> scopePaths,
            String contextDocumentId,
            boolean lowConfidence,
            String scopeRouteReason
    ) {
        List<String> scopes = scopePaths == null ? List.of() : scopePaths;
        if (contextDocumentId != null && !contextDocumentId.isBlank()) {
            FunnelResult pinned = FunnelResult.pinned(scopes, contextDocumentId, scopeRouteReason, lowConfidence);
            return new FunnelDegradationPolicy.AppliedFunnel(
                    pinned,
                    scopes,
                    List.of(),
                    List.of(contextDocumentId)
            );
        }

        TopicRanker.TopicRankingResult topicRanking = topicRanker.rank(query, scopes);
        DocumentRanker.DocumentRankingResult documentRanking = documentRanker.rank(
                query,
                topicRanking.topTopicPaths(),
                scopes
        );
        FunnelDegradationPolicy.AppliedFunnel applied = degradationPolicy.apply(
                null,
                lowConfidence,
                scopes,
                topicRanking,
                documentRanking
        );
        FunnelResult withReason = new FunnelResult(
                applied.funnelResult().scopePaths(),
                applied.funnelResult().topicPaths(),
                applied.funnelResult().lockedDocumentIds(),
                applied.funnelResult().topicConfidence(),
                applied.funnelResult().documentConfidence(),
                applied.funnelResult().degradationLevel(),
                scopeRouteReason,
                lowConfidence,
                applied.funnelResult().documentRanking()
        );
        return new FunnelDegradationPolicy.AppliedFunnel(
                withReason,
                applied.retrievalScopePaths(),
                applied.retrievalTopicPaths(),
                applied.retrievalDocumentIds()
        );
    }
}
