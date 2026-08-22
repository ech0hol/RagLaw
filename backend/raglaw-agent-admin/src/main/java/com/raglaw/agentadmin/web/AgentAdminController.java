package com.raglaw.agentadmin.web;

import com.raglaw.agentadmin.dto.AgentConfigDto;
import com.raglaw.agentadmin.dto.AgentConfigUpdateRequest;
import com.raglaw.agentadmin.service.AgentConfigService;
import com.raglaw.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agents")
public class AgentAdminController {

    private final AgentConfigService agentConfigService;

    public AgentAdminController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    @GetMapping
    public ApiResponse<List<AgentConfigDto>> list() {
        return ApiResponse.ok(agentConfigService.list());
    }

    @GetMapping("/{code}")
    public ApiResponse<AgentConfigDto> get(@PathVariable String code) {
        return ApiResponse.ok(agentConfigService.get(code));
    }

    @PutMapping("/{code}")
    public ApiResponse<AgentConfigDto> update(
            @PathVariable String code,
            @RequestBody AgentConfigUpdateRequest request
    ) {
        return ApiResponse.ok(agentConfigService.update(code, request));
    }

    @PostMapping("/reload")
    public ApiResponse<Map<String, Object>> reload() {
        agentConfigService.reload();
        return ApiResponse.ok(Map.of("reloaded", true));
    }
}
