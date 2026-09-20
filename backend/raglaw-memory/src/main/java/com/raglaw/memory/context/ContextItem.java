package com.raglaw.memory.context;

import java.util.List;

public record ContextItem(String id, ContextSectionType type, ContextPriority priority,
                          String content, int estimatedTokens, boolean lossyCompressionAllowed,
                          List<String> sourceRefs) {
    public ContextItem {
        if (id == null || id.isBlank() || type == null || priority == null) throw new IllegalArgumentException("context item identity");
        content = content == null ? "" : content;
        if (estimatedTokens < 0) throw new IllegalArgumentException("estimatedTokens");
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
    }
}
