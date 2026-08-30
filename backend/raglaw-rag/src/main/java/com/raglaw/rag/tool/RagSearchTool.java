package com.raglaw.rag.tool;

import java.util.List;
import java.util.Map;

public interface RagSearchTool {

    List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit, String agentCode);

    default List<RagSearchHit> search(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId
    ) {
        if (documentId == null || documentId.isBlank()) {
            return search(query, knowledgeScopes, limit, agentCode);
        }
        return searchScopedToDocument(query, knowledgeScopes, limit, agentCode, documentId);
    }

    default RagSearchResult searchDetailed(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId,
            boolean lowConfidence,
            String scopeRouteReason
    ) {
        List<RagSearchHit> hits = search(query, knowledgeScopes, limit, agentCode, documentId);
        return new RagSearchResult(hits, null, Map.of());
    }

    List<RagSearchHit> searchScopedToDocument(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId
    );
}

