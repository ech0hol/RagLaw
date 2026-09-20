package com.raglaw.memory.context;

import java.util.List;

public record CompactionRequest(List<ContextItem> items, int maxTokens) {
    public CompactionRequest { items = items == null ? List.of() : List.copyOf(items); if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens"); }
}
