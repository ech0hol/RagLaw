package com.raglaw.memory.context;

import java.util.List;

public record CompactionResult(List<ContextItem> items, List<String> appliedStages, StructuredConversationSummary summary) {
    public CompactionResult { items = items == null ? List.of() : List.copyOf(items); appliedStages = appliedStages == null ? List.of() : List.copyOf(appliedStages); }
}
