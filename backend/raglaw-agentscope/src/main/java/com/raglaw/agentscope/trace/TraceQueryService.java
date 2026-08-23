package com.raglaw.agentscope.trace;

import com.raglaw.agentscope.domain.RagTraceChunkEntity;
import com.raglaw.agentscope.domain.RagTraceChunkRepository;
import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.domain.RagTraceStageRepository;
import com.raglaw.agentscope.dto.TraceChunkDto;
import com.raglaw.agentscope.dto.TraceDetailDto;
import com.raglaw.agentscope.dto.TraceStageDto;
import com.raglaw.agentscope.dto.TraceSummaryDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TraceQueryService {

    private final RagTraceRepository traceRepository;
    private final RagTraceStageRepository stageRepository;
    private final RagTraceChunkRepository chunkRepository;
    private final LangfuseBridge langfuseBridge;

    public TraceQueryService(
            RagTraceRepository traceRepository,
            RagTraceStageRepository stageRepository,
            RagTraceChunkRepository chunkRepository,
            LangfuseBridge langfuseBridge
    ) {
        this.traceRepository = traceRepository;
        this.stageRepository = stageRepository;
        this.chunkRepository = chunkRepository;
        this.langfuseBridge = langfuseBridge;
    }

    public List<TraceSummaryDto> listRecent() {
        return traceRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public TraceDetailDto getDetail(String traceId) {
        RagTraceEntity trace = traceRepository.findById(traceId)
                .orElseThrow(() -> new IllegalArgumentException("Trace not found: " + traceId));
        List<TraceStageDto> stages = stageRepository.findByTraceId(traceId).stream()
                .map(this::toStage)
                .toList();
        List<TraceChunkDto> chunks = chunkRepository.findByTraceIdOrderByScoreDesc(traceId).stream()
                .map(this::toChunk)
                .toList();
        return new TraceDetailDto(toSummary(trace), stages, chunks);
    }

    public List<RagTraceStageEntity> listStages(String traceId) {
        return stageRepository.findByTraceId(traceId);
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
}
