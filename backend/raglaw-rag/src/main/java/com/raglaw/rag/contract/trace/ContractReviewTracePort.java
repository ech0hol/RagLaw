package com.raglaw.rag.contract.trace;

import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import java.util.Map;

/**
 * Optional observability hook for contract LLM review (implemented in raglaw-agentscope).
 */
public interface ContractReviewTracePort {

    String start(String userId, String documentId, String title);

    void stage(String traceId, String stage, Map<String, Object> detail, long durationMs);

    void recordRagHits(String traceId, List<RagSearchHit> hits);

    void recordLlmUsage(
            String traceId,
            String model,
            int promptTokens,
            int completionTokens,
            long durationMs
    );

    void complete(String traceId, long latencyMs, int riskCount);
}
