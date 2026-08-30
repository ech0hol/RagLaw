package com.raglaw.rag.retrieval;

import com.raglaw.rag.config.RagProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FullTextScoreFilter {

    private final RagProperties ragProperties;

    public FullTextScoreFilter(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<ScoredRow> filter(List<ScoredRow> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        double top = hits.get(0).score();
        if (top <= 0) {
            return hits;
        }
        double threshold = top * ragProperties.getRetrieval().getFulltextMinRatio();
        List<ScoredRow> filtered = new ArrayList<>();
        for (ScoredRow hit : hits) {
            if (hit.score() >= threshold) {
                filtered.add(hit);
            }
        }
        return filtered;
    }

    public record ScoredRow(String documentId, String categoryPath, double score) {
    }
}
