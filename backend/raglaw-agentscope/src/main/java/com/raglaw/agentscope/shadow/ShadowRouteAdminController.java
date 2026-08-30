package com.raglaw.agentscope.shadow;

import com.raglaw.agentscope.shadow.ShadowRouteQueryService.ShadowRouteLogDto;
import com.raglaw.agentscope.shadow.ShadowRouteQueryService.ShadowRouteSummaryDto;
import com.raglaw.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/shadow-routes")
public class ShadowRouteAdminController {

    private final ShadowRouteQueryService shadowRouteQueryService;

    public ShadowRouteAdminController(ShadowRouteQueryService shadowRouteQueryService) {
        this.shadowRouteQueryService = shadowRouteQueryService;
    }

    @GetMapping
    public ApiResponse<List<ShadowRouteLogDto>> list(@RequestParam("traceId") String traceId) {
        return ApiResponse.ok(shadowRouteQueryService.listByTrace(traceId));
    }

    @GetMapping("/summary")
    public ApiResponse<ShadowRouteSummaryDto> summary(@RequestParam(value = "days", defaultValue = "7") int days) {
        return ApiResponse.ok(shadowRouteQueryService.summary(Math.max(1, days)));
    }
}
