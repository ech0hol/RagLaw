package com.raglaw.rag.tool;

import java.util.List;

public interface RagSearchTool {

    List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit);
}
