package com.raglaw.agentscope.trace;

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
import com.raglaw.agentscope.domain.TaskRouteDecisionEntity;
import com.raglaw.agentscope.domain.TaskRouteDecisionRepository;
import com.raglaw.agentscope.dto.TraceA2aCallDto;
import com.raglaw.agentscope.dto.TraceChunkDto;
import com.raglaw.agentscope.dto.TraceDetailDto;
import com.raglaw.agentscope.dto.TraceListPageDto;
import com.raglaw.agentscope.dto.TraceLlmUsageDto;
import com.raglaw.agentscope.dto.TraceShadowLogDto;
import com.raglaw.agentscope.dto.TraceStageDto;
import com.raglaw.agentscope.dto.TraceSummaryDto;
import com.raglaw.agentscope.shadow.ShadowRouteQueryService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TraceQueryService {

    private final RagTraceRepository traceRepository;
    private final RagTraceStageRepository stageRepository;
    private final RagTraceChunkRepository chunkRepository;
    private final LlmUsageLogRepository llmUsageLogRepository;
    private final A2aCallLogRepository a2aCallLogRepository;
    private final LangfuseBridge langfuseBridge;
    private final ShadowRouteQueryService shadowRouteQueryService;
    private final TaskRouteDecisionRepository taskRouteDecisionRepository;

    public TraceQueryService(
            RagTraceRepository traceRepository,
            RagTraceStageRepository stageRepository,
            RagTraceChunkRepository chunkRepository,
            LlmUsageLogRepository llmUsageLogRepository,
            A2aCallLogRepository a2aCallLogRepository,
            LangfuseBridge langfuseBridge,
            ShadowRouteQueryService shadowRouteQueryService,
            TaskRouteDecisionRepository taskRouteDecisionRepository
    ) {
        this.traceRepository = traceRepository;
        this.stageRepository = stageRepository;
        this.chunkRepository = chunkRepository;
        this.llmUsageLogRepository = llmUsageLogRepository;
        this.a2aCallLogRepository = a2aCallLogRepository;
        this.langfuseBridge = langfuseBridge;
        this.shadowRouteQueryService = shadowRouteQueryService;
        this.taskRouteDecisionRepository = taskRouteDecisionRepository;
    }

    public List<TraceSummaryDto> listRecent() {
        return traceRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public TraceListPageDto listPage(int page, int pageSize, String agentCode, String q) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        Page<RagTraceEntity> result = traceRepository.search(
                blankToNull(agentCode),
                blankToNull(q),
                PageRequest.of(safePage - 1, safeSize)
        );
        return new TraceListPageDto(
                result.getContent().stream().map(this::toSummary).toList(),
                safePage,
                safeSize,
                result.getTotalElements()
        );
    }

    public TraceDetailDto getDetail(String traceId) {
        RagTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + traceId));
        List<TraceStageDto> stages = TraceStageOrdering.sort(
                stageRepository.findByTraceId(traceId).stream()
                        .map(this::toStage)
                        .toList()
        );
        List<TraceChunkDto> chunks = chunkRepository.findByTraceIdOrderByScoreDesc(traceId).stream()
                .map(this::toChunk)
                .toList();
        List<TraceLlmUsageDto> llmUsage = llmUsageLogRepository.findByTraceId(traceId).stream()
                .map(this::toLlmUsage)
                .toList();
        List<TraceA2aCallDto> a2aCalls = a2aCallLogRepository.findByTraceId(traceId).stream()
                .map(this::toA2aCall)
                .toList();
        List<TraceShadowLogDto> shadowLogs = shadowRouteQueryService.listByTrace(traceId).stream()
                .map(this::toShadowLog)
                .toList();
        return new TraceDetailDto(toSummary(trace), stages, chunks, llmUsage, a2aCalls, shadowLogs);
    }

    public List<RagTraceStageEntity> listStages(String traceId) {
        return stageRepository.findByTraceId(traceId);
    }

    /** Shadow candidate decisions are additive trace evidence; they never affect execution. */
    public List<TaskRouteDecisionEntity> listTaskRouteDecisions(String traceId) {
        return taskRouteDecisionRepository.findByTraceIdOrderByCreatedAtAsc(traceId);
    }

    private TraceStageDto toStage(RagTraceStageEntity stage) {
        return new TraceStageDto(stage.getId(), stage.getStage(), stage.getDetailJson(), stage.getDurationMs());
    }

    private TraceChunkDto toChunk(RagTraceChunkEntity chunk) {
        return new TraceChunkDto(
                chunk.getId(),
                chunk.getChunkId(),
                chunk.getScore(),
                chunk.getPath(),
                chunk.getExcerpt()
        );
    }

    private TraceLlmUsageDto toLlmUsage(LlmUsageLogEntity entity) {
        return new TraceLlmUsageDto(
                entity.getModel(),
                entity.getPromptTokens(),
                entity.getCompletionTokens(),
                entity.getOutputText()
        );
    }

    private TraceA2aCallDto toA2aCall(A2aCallLogEntity entity) {
        return new TraceA2aCallDto(
                entity.getFromAgent(),
                entity.getToAgent(),
                entity.getInputSummary(),
                entity.getOutputSummary(),
                entity.getLatencyMs()
        );
    }

    private TraceShadowLogDto toShadowLog(ShadowRouteQueryService.ShadowRouteLogDto log) {
        return new TraceShadowLogDto(
                log.id(),
                log.shadowType(),
                log.userValue(),
                log.systemValue(),
                log.hit(),
                log.rank(),
                log.confidenceJson(),
                log.createdAt()
        );
    }

    private TraceSummaryDto toSummary(RagTraceEntity trace) {
        String langfuseTraceId = trace.getLangfuseTraceId();
        return new TraceSummaryDto(
                trace.getId(),
                trace.getConversationId(),
                trace.getAgentCode(),
                trace.getQueryText(),
                trace.getLatencyMs(),
                langfuseTraceId,
                langfuseTraceId == null ? null : langfuseBridge.traceUrl(langfuseTraceId),
                trace.getCreatedAt()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
