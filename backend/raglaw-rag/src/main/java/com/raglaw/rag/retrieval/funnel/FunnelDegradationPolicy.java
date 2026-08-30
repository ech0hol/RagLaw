package com.raglaw.rag.retrieval.funnel;

import com.raglaw.rag.config.RagProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FunnelDegradationPolicy {

    private final RagProperties ragProperties;

    public FunnelDegradationPolicy(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public AppliedFunnel apply(
            String contextDocumentId,
            boolean lowConfidence,
            List<String> scopePaths,
            TopicRanker.TopicRankingResult topicRanking,
            DocumentRanker.DocumentRankingResult documentRanking
    ) {
        if (contextDocumentId != null && !contextDocumentId.isBlank()) {
            return new AppliedFunnel(
                    FunnelResult.pinned(scopePaths, contextDocumentId, "pinned_document", lowConfidence),
                    scopePaths,
                    List.of(),
                    List.of(contextDocumentId)
            );
        }

        double docThreshold = ragProperties.getRetrieval().getDocumentConfidenceThreshold();
        double topicThreshold = ragProperties.getRetrieval().getTopicConfidenceThreshold();

        String degradation;
        List<String> effectiveTopicPaths;
        List<String> effectiveDocumentIds;

        if (documentRanking.documentConfidence() >= docThreshold && !documentRanking.lockedDocumentIds().isEmpty()) {
            degradation = "DOCUMENT";
            effectiveTopicPaths = topicRanking.topTopicPaths();
            effectiveDocumentIds = documentRanking.lockedDocumentIds();
        } else if (topicRanking.topicConfidence() >= topicThreshold && !topicRanking.topTopicPaths().isEmpty()) {
            degradation = "TOPIC";
            effectiveTopicPaths = topicRanking.topTopicPaths();
            effectiveDocumentIds = List.of();
        } else if (lowConfidence) {
            degradation = "SCOPE";
            effectiveTopicPaths = List.of();
            effectiveDocumentIds = List.of();
        } else {
            degradation = "SCOPE";
            effectiveTopicPaths = topicRanking.topTopicPaths();
            effectiveDocumentIds = List.of();
        }

        FunnelResult funnelResult = new FunnelResult(
                scopePaths,
                topicRanking.topTopicPaths(),
                documentRanking.lockedDocumentIds(),
                topicRanking.topicConfidence(),
                documentRanking.documentConfidence(),
                degradation,
                "",
                lowConfidence,
                documentRanking.ranking()
        );
        return new AppliedFunnel(funnelResult, scopePaths, effectiveTopicPaths, effectiveDocumentIds);
    }

    public record AppliedFunnel(
            FunnelResult funnelResult,
            List<String> retrievalScopePaths,
            List<String> retrievalTopicPaths,
            List<String> retrievalDocumentIds
    ) {
    }
}
