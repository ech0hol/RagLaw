package com.raglaw.agentscope.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.LlmUsageLogEntity;
import com.raglaw.agentscope.domain.LlmUsageLogRepository;
import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.domain.RagTraceStageRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TraceRecorder {

    private final RagTraceRepository traceRepository;
    private final RagTraceStageRepository stageRepository;
    private final LlmUsageLogRepository usageLogRepository;
    private final ObjectMapper objectMapper;

    public TraceRecorder(
            RagTraceRepository traceRepository,
            RagTraceStageRepository stageRepository,
            LlmUsageLogRepository usageLogRepository,
            ObjectMapper objectMapper
    ) {
        this.traceRepository = traceRepository;
        this.stageRepository = stageRepository;
        this.usageLogRepository = usageLogRepository;
        this.objectMapper = objectMapper;
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
