package com.raglaw.agentscope.agui;

import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.dto.TraceDetailDto;
import com.raglaw.agentscope.dto.TraceSummaryDto;
import com.raglaw.agentscope.trace.TraceQueryService;
import com.raglaw.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/traces")
public class TraceAdminController {

    private final TraceQueryService traceQueryService;

    public TraceAdminController(TraceQueryService traceQueryService) {
        this.traceQueryService = traceQueryService;
    }

    @GetMapping
    public ApiResponse<List<TraceSummaryDto>> list() {
        return ApiResponse.ok(traceQueryService.listRecent());
    }

    @GetMapping("/{traceId}")
    public ApiResponse<TraceDetailDto> get(@PathVariable("traceId") String traceId) {
        return ApiResponse.ok(traceQueryService.getDetail(traceId));
    }

    @GetMapping("/{traceId}/stages")
    public ApiResponse<List<RagTraceStageEntity>> stages(@PathVariable("traceId") String traceId) {
        return ApiResponse.ok(traceQueryService.listStages(traceId));
    }
}
