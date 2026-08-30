package com.raglaw.rag.retrieval.funnel;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.CategoryRepository;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DocumentRanker {

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;
    private final HybridScorer hybridScorer;
    private final RagProperties ragProperties;

    public DocumentRanker(
            DocumentRepository documentRepository,
            CategoryRepository categoryRepository,
            HybridScorer hybridScorer,
            RagProperties ragProperties
    ) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
        this.hybridScorer = hybridScorer;
        this.ragProperties = ragProperties;
    }

    public DocumentRankingResult rank(String query, List<String> topicPaths, List<String> scopePaths) {
        Map<String, String> categoryPathById = new HashMap<>();
        for (CategoryEntity category : categoryRepository.findByEnabledTrueOrderBySortOrderAsc()) {
            categoryPathById.put(category.getId(), category.getPath());
        }

        List<DocumentEntity> indexed = documentRepository.findByStatusOrderByCreatedAtDesc(DocStatus.INDEXED);
        int maxCandidates = ragProperties.getRetrieval().getFunnelMaxDocumentCandidates();
        List<ScoredDocument> scored = new ArrayList<>();
        for (DocumentEntity document : indexed) {
            String categoryPath = categoryPathById.get(document.getCategoryId());
            if (categoryPath == null) {
                continue;
            }
            if (!matchesTopicOrScope(categoryPath, topicPaths, scopePaths)) {
                continue;
            }
            double score = hybridScorer.scoreDocument(query, document.getTitle(), categoryPath, topicPaths);
            if (score > 0.0) {
                scored.add(new ScoredDocument(document.getId(), document.getTitle(), categoryPath, score));
            }
            if (scored.size() >= maxCandidates) {
                break;
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());
        int topN = ragProperties.getRetrieval().getFunnelTopDocuments();
        List<String> lockedIds = scored.stream().limit(topN).map(ScoredDocument::documentId).toList();
        List<DocumentCandidate> ranking = scored.stream()
                .map(s -> new DocumentCandidate(s.documentId(), s.title(), s.categoryPath(), s.score()))
                .toList();
        double confidence = confidenceOf(scored);
        return new DocumentRankingResult(lockedIds, confidence, ranking);
    }

    private static boolean matchesTopicOrScope(
            String categoryPath,
            List<String> topicPaths,
            List<String> scopePaths
    ) {
        if (topicPaths != null && !topicPaths.isEmpty()) {
            for (String topicPath : topicPaths) {
                if (categoryPath.equals(topicPath) || categoryPath.startsWith(topicPath + "/")) {
                    return true;
                }
            }
        }
        if (scopePaths == null || scopePaths.isEmpty()) {
            return true;
        }
        for (String scope : scopePaths) {
            if (categoryPath.equals(scope) || categoryPath.startsWith(scope + "/")) {
                return true;
            }
        }
        return false;
    }

    private static double confidenceOf(List<ScoredDocument> scored) {
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

    public record DocumentRankingResult(
            List<String> lockedDocumentIds,
            double documentConfidence,
            List<DocumentCandidate> ranking
    ) {
    }

    private record ScoredDocument(String documentId, String title, String categoryPath, double score) {
    }
}
