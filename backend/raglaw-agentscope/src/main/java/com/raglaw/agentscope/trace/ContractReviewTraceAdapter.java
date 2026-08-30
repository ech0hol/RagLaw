package com.raglaw.agentscope.trace;

import com.raglaw.rag.contract.trace.ContractReviewTracePort;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ContractReviewTraceAdapter implements ContractReviewTracePort {

    private final TraceRecorder traceRecorder;

    public ContractReviewTraceAdapter(TraceRecorder traceRecorder) {
        this.traceRecorder = traceRecorder;
    }

    @Override
    public String start(String userId, String documentId, String title) {
        String query = "审查：" + (title == null || title.isBlank() ? documentId : title);
        TraceContext context = traceRecorder.start(null, userId, query, "CONTRACT");
        traceRecorder.recordStage(
                context.traceId(),
                "contract_review",
                Map.of("documentId", documentId, "title", title == null ? "" : title),
                0L
        );
        return context.traceId();
    }

    @Override
    public void stage(String traceId, String stage, Map<String, Object> detail, long durationMs) {
        traceRecorder.recordStage(traceId, stage, detail, durationMs);
    }

    @Override
    public void recordRagHits(String traceId, List<RagSearchHit> hits) {
        traceRecorder.recordChunks(traceId, hits);
    }

    @Override
    public void recordLlmUsage(
            String traceId,
            String model,
            int promptTokens,
            int completionTokens,
            long durationMs
    ) {
        traceRecorder.recordLlmUsage(traceId, model, promptTokens, completionTokens, null, durationMs);
    }

    @Override
    public void complete(String traceId, long latencyMs, int riskCount) {
        traceRecorder.recordStage(
                traceId,
                "contract_review_complete",
                Map.of("riskCount", riskCount),
                0L
        );
        traceRecorder.complete(traceId, latencyMs);
    }
}
