package com.raglaw.agentscope.trace;

import com.raglaw.agentscope.dto.TraceStageDto;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class TraceStageOrdering {

    private static final Map<String, Integer> ORDER = Map.ofEntries(
            Map.entry("contract_review", 0),
            Map.entry("contract_rag_context", 10),
            Map.entry("knowledge_funnel", 20),
            Map.entry("dual_channel_retrieval", 30),
            Map.entry("rag_search", 40),
            Map.entry("contract_llm_batch", 50),
            Map.entry("mcp_tool_call", 60),
            Map.entry("a2a_delegate", 70),
            Map.entry("llm", 80),
            Map.entry("contract_review_complete", 90)
    );

    private TraceStageOrdering() {
    }

    public static List<TraceStageDto> sort(List<TraceStageDto> stages) {
        return stages.stream()
                .sorted(Comparator
                        .comparingInt((TraceStageDto stage) -> ORDER.getOrDefault(stage.stage(), 999))
                        .thenComparing(stage -> stage.id() == null ? "" : stage.id()))
                .toList();
    }
}
