package com.raglaw.rag.retrieval;

import com.raglaw.rag.dto.RetrievalHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RetrievalDiversifier {

    public List<RetrievalHit> diversify(List<RetrievalHit> ranked, int limit, int maxPerDocument) {
        if (ranked == null || ranked.isEmpty() || limit <= 0) {
            return List.of();
        }
        int perDocLimit = Math.max(1, maxPerDocument);
        Map<String, Integer> counts = new HashMap<>();
        List<RetrievalHit> selected = new ArrayList<>();
        for (RetrievalHit hit : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            String documentId = hit.documentId() == null ? hit.chunkId() : hit.documentId();
            int used = counts.getOrDefault(documentId, 0);
            if (used >= perDocLimit) {
                continue;
            }
            counts.put(documentId, used + 1);
            selected.add(hit);
        }
        return selected;
    }
}
