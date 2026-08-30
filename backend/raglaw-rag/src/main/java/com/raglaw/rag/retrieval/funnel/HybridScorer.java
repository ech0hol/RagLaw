package com.raglaw.rag.retrieval.funnel;

import com.raglaw.rag.service.EmbeddingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HybridScorer {

    private static final Map<String, List<String>> KEYWORD_PATH_HINTS = Map.of(
            "LABOR", List.of("劳动", "工资", "加班", "工伤", "社保", "解雇", "辞退", "拖欠"),
            "CONTRACT", List.of("合同", "违约", "解除", "赔偿", "条款", "履行"),
            "CIVIL", List.of("民事", "侵权", "债务", "物权", "人格权"),
            "ADMIN", List.of("行政", "许可", "处罚", "危化", "化学品", "危险化学品", "监管"),
            "CRIMINAL", List.of("刑事", "犯罪", "刑罚", "拘留")
    );

    private final EmbeddingService embeddingService;

    public HybridScorer(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public double scoreTopic(String query, String categoryPath, String categoryName, String categoryCode) {
        String corpus = categoryName + " " + categoryCode + " " + categoryPath;
        double lexical = lexicalScore(query, corpus);
        double keyword = keywordEntityScore(query, categoryPath);
        double semantic = semanticScore(query, categoryName);
        return 0.45 * lexical + 0.35 * keyword + 0.20 * semantic;
    }

    public double scoreDocument(String query, String title, String categoryPath, List<String> topicPaths) {
        double titleLexical = lexicalScore(query, title);
        double pathMatch = pathOverlapScore(categoryPath, topicPaths);
        double semantic = semanticScore(query, title);
        return 0.50 * titleLexical + 0.30 * pathMatch + 0.20 * semantic;
    }

    private double lexicalScore(String query, String text) {
        if (query == null || query.isBlank() || text == null || text.isBlank()) {
            return 0.0;
        }
        String normalizedQuery = normalize(query);
        String normalizedText = normalize(text);
        if (normalizedText.contains(normalizedQuery)) {
            return 1.0;
        }
        int hits = 0;
        int tokens = 0;
        for (String token : tokenize(query)) {
            if (token.length() < 2) {
                continue;
            }
            tokens++;
            if (normalizedText.contains(token)) {
                hits++;
            }
        }
        if (tokens == 0) {
            return 0.0;
        }
        return (double) hits / tokens;
    }

    private double keywordEntityScore(String query, String categoryPath) {
        if (query == null || categoryPath == null) {
            return 0.0;
        }
        String normalizedQuery = normalize(query);
        double best = 0.0;
        for (Map.Entry<String, List<String>> entry : KEYWORD_PATH_HINTS.entrySet()) {
            if (!categoryPath.toUpperCase().contains(entry.getKey())) {
                continue;
            }
            int hits = 0;
            for (String keyword : entry.getValue()) {
                if (normalizedQuery.contains(normalize(keyword))) {
                    hits++;
                }
            }
            if (hits > 0) {
                best = Math.max(best, Math.min(1.0, hits / 2.0));
            }
        }
        return best;
    }

    private double semanticScore(String query, String text) {
        if (!embeddingService.isEnabled()) {
            return 0.0;
        }
        Optional<float[]> queryVector = embeddingService.embed(query);
        Optional<float[]> textVector = embeddingService.embed(text);
        if (queryVector.isEmpty() || textVector.isEmpty()) {
            return 0.0;
        }
        return cosine(queryVector.get(), textVector.get());
    }

    private static double pathOverlapScore(String categoryPath, List<String> topicPaths) {
        if (categoryPath == null || topicPaths == null || topicPaths.isEmpty()) {
            return 0.0;
        }
        double best = 0.0;
        for (String topicPath : topicPaths) {
            if (topicPath == null || topicPath.isBlank()) {
                continue;
            }
            if (categoryPath.equals(topicPath)) {
                return 1.0;
            }
            if (categoryPath.startsWith(topicPath + "/") || topicPath.startsWith(categoryPath + "/")) {
                best = Math.max(best, 0.7);
            }
        }
        return best;
    }

    private static double cosine(float[] left, float[] right) {
        if (left.length != right.length || left.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", "");
    }

    private static List<String> tokenize(String value) {
        String[] parts = value.trim().split("[\\s，。；、？！,.;!?]+");
        return List.of(parts);
    }
}
