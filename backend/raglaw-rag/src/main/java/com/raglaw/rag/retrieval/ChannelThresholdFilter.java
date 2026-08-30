package com.raglaw.rag.retrieval;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.dto.RetrievalHit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChannelThresholdFilter {

    private final RagProperties ragProperties;

    public ChannelThresholdFilter(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RetrievalHit> filterVector(List<RetrievalHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        double min = ragProperties.getRetrieval().getVectorMinSimilarity();
        List<RetrievalHit> filtered = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            if (hit.score() >= min) {
                filtered.add(hit);
            }
        }
        return filtered;
    }

    public List<RetrievalHit> filterFullText(List<RetrievalHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        double top = hits.get(0).score();
        if (top <= 0) {
            return hits;
        }
        double ratio = ragProperties.getRetrieval().getFulltextMinRatio();
        double threshold = top * ratio;
        List<RetrievalHit> filtered = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            if (hit.score() >= threshold) {
                filtered.add(hit);
            }
        }
        return filtered;
    }
}
