package com.raglaw.rag.tool;

import java.util.List;

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

    List<RagSearchHit> searchScopedToDocument(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId
    );
}
