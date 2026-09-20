package com.raglaw.memory.context;

import java.util.List;

public record AssembledContext(String profileCode, long memorySnapshotVersion, List<ContextItem> items,
                               List<String> omittedIds, int estimatedInputTokens, int inputBudget,
                               int outputReserve) {
    public AssembledContext {
        items = items == null ? List.of() : List.copyOf(items);
        omittedIds = omittedIds == null ? List.of() : List.copyOf(omittedIds);
    }
    public List<String> includedIds() { return items.stream().map(ContextItem::id).toList(); }
}
