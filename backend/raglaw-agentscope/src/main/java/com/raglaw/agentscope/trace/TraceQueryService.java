package com.raglaw.agentscope.trace;

import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.domain.RagTraceStageRepository;
import com.raglaw.agentscope.dto.TraceSummaryDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TraceQueryService {

    private final RagTraceRepository traceRepository;
    private final RagTraceStageRepository stageRepository;

    public TraceQueryService(RagTraceRepository traceRepository, RagTraceStageRepository stageRepository) {
        this.traceRepository = traceRepository;
        this.stageRepository = stageRepository;
    }

    public List<TraceSummaryDto> listRecent() {
        return traceRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public List<RagTraceStageEntity> listStages(String traceId) {
        return stageRepository.findByTraceId(traceId);
    }

    private TraceSummaryDto toSummary(RagTraceEntity trace) {
        return new TraceSummaryDto(
                trace.getId(),
                trace.getConversationId(),
                trace.getAgentCode(),
                trace.getQueryText(),
                trace.getLatencyMs(),
                trace.getCreatedAt()
        );
    }
}
