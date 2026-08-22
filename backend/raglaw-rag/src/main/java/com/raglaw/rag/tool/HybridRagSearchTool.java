package com.raglaw.rag.tool;

import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.retrieval.HybridRetriever;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HybridRagSearchTool implements RagSearchTool {

    private final HybridRetriever hybridRetriever;

    public HybridRagSearchTool(HybridRetriever hybridRetriever) {
        this.hybridRetriever = hybridRetriever;
    }

    @Override
    public List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit) {
        return hybridRetriever.search(query, knowledgeScopes, limit).stream()
                .map(this::toHit)
                .toList();
    }

    private RagSearchHit toHit(RetrievalHit hit) {
        String path = hit.l1Path() + "/" + hit.l2Path() + "/" + hit.l3Path();
        String excerpt = hit.content().length() <= 200 ? hit.content() : hit.content().substring(0, 200) + "…";
        return new RagSearchHit(hit.chunkId(), hit.score(), path, excerpt);
    }
}
