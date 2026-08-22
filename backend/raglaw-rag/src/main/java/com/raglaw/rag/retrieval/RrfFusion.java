package com.raglaw.rag.retrieval;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    private RrfFusion() {
    }

    public static List<RetrievalHit> fuse(List<List<RetrievalHit>> rankedLists, int k, int topN) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, RetrievalHit> hits = new HashMap<>();

        for (List<RetrievalHit> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RetrievalHit hit = list.get(rank);
                double rrfScore = 1.0 / (k + rank + 1);
                scores.merge(hit.chunkId(), rrfScore, Double::sum);
                hits.putIfAbsent(hit.chunkId(), hit);
            }
        }

        List<RetrievalHit> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            fused.add(hits.get(entry.getKey()).withScore(entry.getValue()));
        }
        fused.sort(Comparator.comparingDouble(RetrievalHit::score).reversed());
        if (fused.size() > topN) {
            return fused.subList(0, topN);
        }
        return fused;
    }
}
