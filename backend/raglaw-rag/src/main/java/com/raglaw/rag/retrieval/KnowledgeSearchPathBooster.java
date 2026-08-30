package com.raglaw.rag.retrieval;

import com.raglaw.rag.retrieval.FullTextScoreFilter.ScoredRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSearchPathBooster {

    public List<ScoredRow> rerank(String query, List<ScoredRow> rows, Map<String, String> documentPaths) {
        if (rows == null || rows.size() <= 1) {
            return rows == null ? List.of() : rows;
        }
        List<ScoredRow> reranked = new ArrayList<>(rows);
        reranked.sort(Comparator.comparingDouble(
                (ScoredRow row) -> boostedScore(query, resolvePath(row, documentPaths), row.score())
        ).reversed());
        return reranked;
    }

    private static String resolvePath(ScoredRow row, Map<String, String> documentPaths) {
        if (row.categoryPath() != null && !row.categoryPath().isBlank()) {
            return row.categoryPath();
        }
        return documentPaths.getOrDefault(row.documentId(), "");
    }

    static double boostedScore(String query, String path, double score) {
        if (path == null || path.isBlank() || score <= 0) {
            return score;
        }
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
        double multiplier = 1.0;
        if (containsAny(normalizedQuery, "加班费", "加班工资", "超时工作")) {
            multiplier *= path.startsWith("/STATUTE") ? 1.6 : 1.0;
            multiplier *= path.startsWith("/CASE") ? 0.55 : 1.0;
        }
        if (containsAny(normalizedQuery, "借贷", "利息", "利率", "借款")) {
            multiplier *= path.startsWith("/STATUTE/CIVIL") ? 1.6 : 1.0;
            multiplier *= path.startsWith("/STATUTE/CRIMINAL") ? 0.55 : 1.0;
        }
        if (containsAny(normalizedQuery, "化学品", "危化品", "危险化学品")) {
            multiplier *= path.startsWith("/STATUTE/CRIMINAL") ? 1.4 : 1.0;
            multiplier *= path.startsWith("/STATUTE/ADMIN") ? 1.2 : 1.0;
        }
        if (containsAny(normalizedQuery, "行政处罚", "罚款", "吊销许可")) {
            multiplier *= path.startsWith("/STATUTE/ADMIN") ? 1.6 : 1.0;
        }
        return score * multiplier;
    }

    private static boolean containsAny(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
