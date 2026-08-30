package com.raglaw.rag.retrieval;

import java.util.List;

public record RetrievalConstraints(
        List<String> scopePaths,
        List<String> topicPaths,
        List<String> documentIds
) {
    public static RetrievalConstraints empty() {
        return new RetrievalConstraints(List.of(), List.of(), List.of());
    }

    public List<String> effectiveScopePaths() {
        if (topicPaths != null && !topicPaths.isEmpty()) {
            return topicPaths;
        }
        return scopePaths == null ? List.of() : scopePaths;
    }

    public boolean hasDocumentFilter() {
        return documentIds != null && !documentIds.isEmpty();
    }
}
