package com.raglaw.rag.retrieval;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RetrievalReranker {

    private static final double STATUTE_BOOST = 1.3;

    public List<RetrievalHit> rerank(List<RetrievalHit> hits, String agentCode) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        boolean preferStatute = agentCode != null && agentCode.startsWith("STATUTE");
        List<RetrievalHit> ranked = new ArrayList<>(hits);
        ranked.sort(Comparator.comparingDouble((RetrievalHit hit) -> boostedScore(hit, preferStatute)).reversed());
        return ranked;
    }

    private static double boostedScore(RetrievalHit hit, boolean preferStatute) {
        double score = hit.score();
        if (preferStatute && "/STATUTE".equals(hit.l1Path())) {
            score *= STATUTE_BOOST;
        }
        return score;
    }
}
