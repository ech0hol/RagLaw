package com.raglaw.agentscope.runtime;

import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mutable per-run collector for rag_search hits and cancellation checks.
 */
public class AgentRunSession {

    private final String taskId;
    private final List<RagSearchHit> hits = new ArrayList<>();
    private final List<com.raglaw.agentscope.agui.WebReference> webReferences = new ArrayList<>();
    private volatile boolean cancelled;
    private volatile com.raglaw.rag.retrieval.funnel.FunnelResult lastFunnelResult;
    private volatile Map<String, Object> lastRetrievalTrace = Map.of();
    private volatile long lastRagSearchLatencyMs = 0L;
    private volatile String traceId;
    private volatile String userMessage;
    private volatile int emittedReferenceCount;
    private volatile boolean presearchCompleted;

    public AgentRunSession(String taskId) {
        this.taskId = taskId;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }

    public int emittedReferenceCount() {
        return emittedReferenceCount;
    }

    public void setEmittedReferenceCount(int count) {
        this.emittedReferenceCount = count;
    }

    public void markPresearchCompleted() {
        this.presearchCompleted = true;
    }

    public boolean presearchCompleted() {
        return presearchCompleted;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String traceId() {
        return traceId;
    }

    public String taskId() {
        return taskId;
    }

    public List<com.raglaw.agentscope.agui.WebReference> webReferences() {
        return Collections.unmodifiableList(webReferences);
    }

    public void addWebReference(com.raglaw.agentscope.agui.WebReference reference) {
        if (reference != null) {
            webReferences.add(reference);
        }
    }

    public void addHits(List<RagSearchHit> newHits) {
        if (newHits == null || newHits.isEmpty()) {
            return;
        }
        for (RagSearchHit hit : newHits) {
            boolean exists = hits.stream().anyMatch(existing -> existing.chunkId().equals(hit.chunkId()));
            if (!exists) {
                hits.add(hit);
            }
        }
    }

    public List<RagSearchHit> hits() {
        return Collections.unmodifiableList(hits);
    }

    public void recordSearch(RagSearchResult result, long latencyMs) {
        if (result == null) {
            return;
        }
        boolean incomingCatalog = result.retrievalTrace() != null
                && Boolean.TRUE.equals(result.retrievalTrace().get("catalogMode"));
        boolean existingCatalog = lastRetrievalTrace != null
                && Boolean.TRUE.equals(lastRetrievalTrace.get("catalogMode"));
        boolean incomingEmpty = result.hits() == null || result.hits().isEmpty();
        if (!incomingCatalog && existingCatalog && incomingEmpty) {
            lastRagSearchLatencyMs = latencyMs;
            return;
        }
        if (!incomingCatalog && !incomingEmpty) {
            hits.removeIf(hit -> hit.chunkId() != null && hit.chunkId().startsWith("catalog-"));
        }
        addHits(result.hits());
        lastFunnelResult = result.funnelResult();
        lastRetrievalTrace = result.retrievalTrace() == null ? Map.of() : result.retrievalTrace();
        lastRagSearchLatencyMs = latencyMs;
    }

    public com.raglaw.rag.retrieval.funnel.FunnelResult lastFunnelResult() {
        return lastFunnelResult;
    }

    public Map<String, Object> lastRetrievalTrace() {
        return lastRetrievalTrace;
    }

    public long lastRagSearchLatencyMs() {
        return lastRagSearchLatencyMs;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
