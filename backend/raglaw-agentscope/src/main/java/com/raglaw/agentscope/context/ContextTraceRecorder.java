package com.raglaw.agentscope.context;

import com.raglaw.agentscope.trace.TraceRecorder;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Records only context metadata; protected fact and document content never enters the trace payload. */
@Component
public class ContextTraceRecorder {
    private final TraceRecorder traceRecorder;

    public ContextTraceRecorder() { this(null); }
    @Autowired
    public ContextTraceRecorder(TraceRecorder traceRecorder) { this.traceRecorder = traceRecorder; }

    public void record(String traceId, String scopeId, String profileCode, String mode, long snapshotVersion,
                       java.util.List<String> includedIds, java.util.List<String> omittedIds,
                       java.util.List<String> stages, int estimatedTokens, String outcome) {
        if (traceRecorder == null || traceId == null || traceId.isBlank()) return;
        traceRecorder.recordStage(traceId, "context_governance", Map.of(
                "scopeId", scopeId == null ? "" : scopeId,
                "profile", profileCode == null ? "" : profileCode,
                "mode", mode == null ? "" : mode,
                "snapshotVersion", snapshotVersion,
                "includedCount", includedIds == null ? 0 : includedIds.size(),
                "omittedCount", omittedIds == null ? 0 : omittedIds.size(),
                "compactionStages", stages == null ? java.util.List.of() : stages,
                "estimatedTokens", estimatedTokens,
                "outcome", outcome == null ? "" : outcome
        ), 0L);
    }
}
