package com.raglaw.agentscope.agui;

import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.dto.TraceDeleteBatchRequest;
import com.raglaw.agentscope.dto.TraceDeleteBatchResultDto;
import com.raglaw.agentscope.dto.TraceDetailDto;
import com.raglaw.agentscope.dto.TraceListPageDto;
import com.raglaw.agentscope.dto.TraceSummaryDto;
import com.raglaw.agentscope.trace.TraceDeletionService;
import com.raglaw.agentscope.trace.TraceQueryService;
import com.raglaw.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/traces")
public class TraceAdminController {

    private final TraceQueryService traceQueryService;
    private final TraceDeletionService traceDeletionService;

    public TraceAdminController(
            TraceQueryService traceQueryService,
            TraceDeletionService traceDeletionService
    ) {
        this.traceQueryService = traceQueryService;
        this.traceDeletionService = traceDeletionService;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "agentCode", required = false) String agentCode,
            @RequestParam(value = "q", required = false) String q
    ) {
        if (page != null || pageSize != null || agentCode != null || q != null) {
            int resolvedPage = page == null ? 1 : page;
            int resolvedSize = pageSize == null ? 20 : pageSize;
            return ApiResponse.ok(traceQueryService.listPage(resolvedPage, resolvedSize, agentCode, q));
        }
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

    @DeleteMapping("/{traceId}")
    public ApiResponse<Void> delete(@PathVariable("traceId") String traceId) {
        traceDeletionService.delete(traceId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<TraceDeleteBatchResultDto> deleteBatch(
            @Valid @RequestBody TraceDeleteBatchRequest request
    ) {
        int deleted = traceDeletionService.deleteBatch(request.traceIds());
        return ApiResponse.ok(new TraceDeleteBatchResultDto(deleted));
    }
}
