package com.raglaw.memory.context;

import java.util.List;

public interface ContextSummarizer {
    StructuredConversationSummary summarize(List<ContextItem> items);
}
