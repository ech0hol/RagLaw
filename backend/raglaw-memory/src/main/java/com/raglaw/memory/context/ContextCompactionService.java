package com.raglaw.memory.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ContextCompactionService {
    private final ContextSummarizer summarizer;
    public ContextCompactionService(java.util.Optional<ContextSummarizer> summarizer) { this.summarizer = summarizer.orElse(items -> new StructuredConversationSummary("", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())); }
    public CompactionResult compact(CompactionRequest request) {
        List<String> stages = new ArrayList<>(); stages.add("DEDUPLICATE");
        Map<String, ContextItem> unique = new LinkedHashMap<>(); request.items().forEach(item -> unique.putIfAbsent(item.id(), item));
        List<ContextItem> current = new ArrayList<>(unique.values());
        int tokens = current.stream().mapToInt(ContextItem::estimatedTokens).sum();
        if (tokens <= request.maxTokens()) return new CompactionResult(current, stages, null);
        stages.add("DROP_P3"); current.removeIf(item -> item.priority() == ContextPriority.P3_OPTIONAL);
        tokens = current.stream().mapToInt(ContextItem::estimatedTokens).sum();
        if (tokens <= request.maxTokens()) return new CompactionResult(current, stages, null);
        stages.add("OFFLOAD_LARGE_ARTIFACT"); current.removeIf(item -> item.type() == ContextSectionType.TOOL_ARTIFACT && item.lossyCompressionAllowed());
        tokens = current.stream().mapToInt(ContextItem::estimatedTokens).sum();
        if (tokens <= request.maxTokens()) return new CompactionResult(current, stages, null);
        stages.add("SUMMARIZE_OLD_TURNS"); StructuredConversationSummary summary = summarizer.summarize(current.stream().filter(ContextItem::lossyCompressionAllowed).toList());
        if (summary == null) throw new InvalidSummaryException("summarizer returned null");
        return new CompactionResult(current, stages, summary);
    }
}
