package com.raglaw.rag.retrieval.funnel;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.repository.CategoryRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TopicRanker {

    private final CategoryRepository categoryRepository;
    private final HybridScorer hybridScorer;
    private final RagProperties ragProperties;

    public TopicRanker(
            CategoryRepository categoryRepository,
            HybridScorer hybridScorer,
            RagProperties ragProperties
    ) {
        this.categoryRepository = categoryRepository;
        this.hybridScorer = hybridScorer;
        this.ragProperties = ragProperties;
    }

    public TopicRankingResult rank(String query, List<String> scopePaths) {
        List<CategoryEntity> candidates = categoryRepository.findByEnabledTrueOrderBySortOrderAsc();
        List<String> scopes = scopePaths == null ? List.of() : scopePaths;
        List<ScoredTopic> scored = new ArrayList<>();
        for (CategoryEntity category : candidates) {
            if (category.getLevel() < 2) {
                continue;
            }
            if (!matchesScope(category.getPath(), scopes)) {
                continue;
            }
            double score = hybridScorer.scoreTopic(
                    query,
                    category.getPath(),
                    category.getName(),
                    category.getCode()
            );
            if (score > 0.0) {
                scored.add(new ScoredTopic(category.getPath(), score));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredTopic::score).reversed());
        int topN = ragProperties.getRetrieval().getFunnelTopTopics();
        List<String> topPaths = scored.stream().limit(topN).map(ScoredTopic::path).toList();
        double confidence = confidenceOf(scored);
        return new TopicRankingResult(topPaths, confidence, scored);
    }

    private static boolean matchesScope(String path, List<String> scopes) {
        if (scopes.isEmpty()) {
            return true;
        }
        for (String scope : scopes) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            if (path.equals(scope) || path.startsWith(scope + "/")) {
                return true;
            }
        }
        return false;
    }

    private static double confidenceOf(List<ScoredTopic> scored) {
        if (scored.isEmpty()) {
            return 0.0;
        }
        if (scored.size() == 1) {
            return scored.get(0).score();
        }
        double top = scored.get(0).score();
        double second = scored.get(1).score();
        double gap = top - second;
        return Math.min(1.0, Math.max(0.0, top * 0.6 + gap * 0.4));
    }

    public record TopicRankingResult(
            List<String> topTopicPaths,
            double topicConfidence,
            List<ScoredTopic> ranking
    ) {
    }

    public record ScoredTopic(String path, double score) {
    }
}
