package com.raglaw.rag.tool;

import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.retrieval.HybridRetriever;
import com.raglaw.rag.service.KnowledgeScopeResolver;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HybridRagSearchTool implements RagSearchTool {

    private final HybridRetriever hybridRetriever;
    private final KnowledgeScopeResolver knowledgeScopeResolver;

    public HybridRagSearchTool(
            HybridRetriever hybridRetriever,
            KnowledgeScopeResolver knowledgeScopeResolver
    ) {
        this.hybridRetriever = hybridRetriever;
        this.knowledgeScopeResolver = knowledgeScopeResolver;
    }

    @Override
    public List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit) {
        List<String> scopePaths = knowledgeScopeResolver.resolvePaths(knowledgeScopes);
        return hybridRetriever.search(query, scopePaths, limit).stream()
                .map(this::toHit)
                .toList();
    }

    private RagSearchHit toHit(RetrievalHit hit) {
        String path = hit.l3Path() != null && !hit.l3Path().isBlank() ? hit.l3Path() : hit.l2Path();
        String excerpt = hit.content().length() <= 200 ? hit.content() : hit.content().substring(0, 200) + "…";
        return new RagSearchHit(hit.chunkId(), hit.score(), path, excerpt);
    }
}
