package com.raglaw.rag.retrieval;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CitationRelevanceFilter {

    private static final int MAX_CITATIONS = 5;

    private final RagProperties ragProperties;

    public CitationRelevanceFilter(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RagSearchHit> filter(List<RagSearchHit> hits, String userQuery) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RagSearchHit> scoreFiltered = filterByRelativeScore(hits);
        List<RagSearchHit> topicFiltered = filterByTopicPreference(scoreFiltered, userQuery);
        return limit(topicFiltered, MAX_CITATIONS);
    }

    private List<RagSearchHit> filterByRelativeScore(List<RagSearchHit> hits) {
        if (hits.isEmpty()) {
            return hits;
        }
        double top = hits.stream().mapToDouble(RagSearchHit::score).max().orElse(0.0);
        if (top <= 0) {
            return hits;
        }
        double ratio = ragProperties.getRetrieval().getFulltextMinRatio();
        double threshold = top * ratio;
        List<RagSearchHit> filtered = new ArrayList<>();
        for (RagSearchHit hit : hits) {
            if (hit.score() >= threshold) {
                filtered.add(hit);
            }
        }
        return filtered.isEmpty() ? List.of(hits.get(0)) : filtered;
    }

    private List<RagSearchHit> filterByTopicPreference(List<RagSearchHit> hits, String userQuery) {
        if (hits.size() <= 1 || userQuery == null || userQuery.isBlank()) {
            return hits;
        }
        String query = userQuery.toLowerCase(Locale.ROOT);
        boolean socialQuery = containsAny(query, "医保", "医疗", "社保", "保险", "报销", "参保");
        boolean criminalQuery = containsAny(query, "刑法", "刑罚", "犯罪", "刑事", "附加刑");
        if (!socialQuery && !criminalQuery) {
            return hits;
        }
        String preferredPrefix = socialQuery ? "/STATUTE/SOCIAL" : "/STATUTE/CRIMINAL";
        boolean hasPreferred = hits.stream().anyMatch(hit -> pathStartsWith(hit.l1L2L3Path(), preferredPrefix));
        if (!hasPreferred) {
            return hits;
        }
        List<RagSearchHit> filtered = new ArrayList<>();
        for (RagSearchHit hit : hits) {
            String path = hit.l1L2L3Path();
            if (pathStartsWith(path, "/STATUTE/CONSTITUTIONAL") && hasPreferred) {
                continue;
            }
            if (socialQuery && pathStartsWith(path, preferredPrefix)) {
                filtered.add(hit);
            } else if (criminalQuery && pathStartsWith(path, preferredPrefix)) {
                filtered.add(hit);
            } else if (!pathStartsWith(path, "/STATUTE/CONSTITUTIONAL")) {
                filtered.add(hit);
            }
        }
        if (filtered.isEmpty()) {
            return hits;
        }
        return filtered;
    }

    private static List<RagSearchHit> limit(List<RagSearchHit> hits, int max) {
        if (hits.size() <= max) {
            return hits;
        }
        return hits.subList(0, max);
    }

    private static boolean containsAny(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathStartsWith(String path, String prefix) {
        return path != null && prefix != null && path.startsWith(prefix);
    }
}
