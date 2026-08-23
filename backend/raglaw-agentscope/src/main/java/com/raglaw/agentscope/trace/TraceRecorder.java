package com.raglaw.agentscope.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.A2aCallLogEntity;
import com.raglaw.agentscope.domain.A2aCallLogRepository;
import com.raglaw.agentscope.domain.LlmUsageLogEntity;
import com.raglaw.agentscope.domain.LlmUsageLogRepository;
import com.raglaw.agentscope.domain.RagTraceChunkEntity;
import com.raglaw.agentscope.domain.RagTraceChunkRepository;
import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.domain.RagTraceStageRepository;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TraceRecorder {

    private final RagTraceRepository traceRepository;
    private final RagTraceStageRepository stageRepository;
    private final LlmUsageLogRepository usageLogRepository;
    private final A2aCallLogRepository a2aCallLogRepository;
    private final RagTraceChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;
    private final LangfuseBridge langfuseBridge;

    public TraceRecorder(
            RagTraceRepository traceRepository,
            RagTraceStageRepository stageRepository,
            LlmUsageLogRepository usageLogRepository,
            A2aCallLogRepository a2aCallLogRepository,
            RagTraceChunkRepository chunkRepository,
            ObjectMapper objectMapper,
            LangfuseBridge langfuseBridge
    ) {
        this.traceRepository = traceRepository;
        this.stageRepository = stageRepository;
        this.usageLogRepository = usageLogRepository;
        this.a2aCallLogRepository = a2aCallLogRepository;
        this.chunkRepository = chunkRepository;
        this.objectMapper = objectMapper;
        this.langfuseBridge = langfuseBridge;
    }

    @Transactional
    public TraceContext start(
            String conversationId,
            String userId,
            String queryText,
            String agentCode
    ) {
        TraceContext context = TraceContext.create();
        traceRepository.save(new RagTraceEntity(
                context.traceId(),
                conversationId,
                context.messageId(),
                userId,
                queryText,
                agentCode
        ));
        langfuseBridge.startTrace(context.traceId(), userId, queryText, agentCode)
                .ifPresent(langfuseTraceId -> traceRepository.findById(context.traceId()).ifPresent(trace -> {
                    trace.setLangfuseTraceId(langfuseTraceId);
                    traceRepository.save(trace);
                }));
        return context;
    }

    @Transactional
    public void recordStage(String traceId, String stage, Map<String, Object> detail, long durationMs) {
        stageRepository.save(new RagTraceStageEntity(
                UUID.randomUUID().toString(),
                traceId,
                stage,
                writeJson(detail),
                durationMs
        ));
        String langfuseTraceId = traceRepository.findById(traceId)
                .map(RagTraceEntity::getLangfuseTraceId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(traceId);
        langfuseBridge.recordSpan(
                langfuseTraceId,
                stage,
                detail,
                Map.of("durationMs", durationMs),
                durationMs
        );
    }

    @Transactional
    public void recordA2aCall(
            String traceId,
            String fromAgent,
            String toAgent,
            String inputSummary,
            String outputSummary,
            long latencyMs
    ) {
        a2aCallLogRepository.save(new A2aCallLogEntity(
                UUID.randomUUID().toString(),
                traceId,
                fromAgent,
                toAgent,
                inputSummary,
                outputSummary,
                latencyMs
        ));
        String langfuseTraceId = traceRepository.findById(traceId)
                .map(RagTraceEntity::getLangfuseTraceId)
                .filter(id -> id != null && !id.isBlank())
                .orElse(traceId);
        langfuseBridge.recordSpan(
                langfuseTraceId,
                "a2a:" + toAgent,
                Map.of("from", fromAgent, "input", inputSummary == null ? "" : inputSummary),
                Map.of("output", outputSummary == null ? "" : outputSummary),
                latencyMs
        );
    }

    @Transactional
    public void recordChunks(String traceId, List<RagSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (RagSearchHit hit : hits) {
            chunkRepository.save(new RagTraceChunkEntity(
                    UUID.randomUUID().toString(),
                    traceId,
                    hit.chunkId(),
                    hit.score(),
                    hit.l1L2L3Path(),
                    hit.excerpt()
            ));
        }
    }

    @Transactional
    public void recordLlmUsage(
            String traceId,
            String model,
            Integer promptTokens,
            Integer completionTokens
    ) {
        usageLogRepository.save(new LlmUsageLogEntity(
                UUID.randomUUID().toString(),
                traceId,
                model,
                promptTokens,
                completionTokens
        ));
    }

    @Transactional
    public void complete(String traceId, long latencyMs) {
        traceRepository.findById(traceId).ifPresent(trace -> {
            trace.setLatencyMs(latencyMs);
            traceRepository.save(trace);
            String langfuseTraceId = trace.getLangfuseTraceId() == null ? traceId : trace.getLangfuseTraceId();
            langfuseBridge.completeTrace(langfuseTraceId, latencyMs);
        });
    }

    private String writeJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
