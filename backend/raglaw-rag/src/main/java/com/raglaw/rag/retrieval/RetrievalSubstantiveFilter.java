package com.raglaw.rag.retrieval;

import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RetrievalSubstantiveFilter {

    public List<RetrievalHit> filter(List<RetrievalHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RetrievalHit> substantive = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            if (!looksLikeTableOfContents(hit.content())) {
                substantive.add(hit);
            }
        }
        return substantive;
    }

    static boolean looksLikeTableOfContents(String content) {
        return ChunkHeadingHeuristics.looksLikeTableOfContents(content);
    }
}
