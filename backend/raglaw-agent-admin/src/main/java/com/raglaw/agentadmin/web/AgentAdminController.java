package com.raglaw.agentadmin.web;

import com.raglaw.agentadmin.dto.AgentConfigCreateRequest;
import com.raglaw.agentadmin.dto.AgentConfigDto;
import com.raglaw.agentadmin.dto.AgentConfigUpdateRequest;
import com.raglaw.agentadmin.dto.AgentToolCatalogDto;
import com.raglaw.agentadmin.service.AgentConfigService;
import com.raglaw.agentadmin.dto.AgentVersionDto;
import com.raglaw.agentadmin.dto.PublishAgentRequest;
import com.raglaw.agentadmin.service.AgentPublicationService;
import com.raglaw.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/v1/admin/agents")
public class AgentAdminController {

    private final AgentConfigService agentConfigService;
    @Autowired(required = false)
    private AgentPublicationService publicationService;

    public AgentAdminController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    @GetMapping("/catalog")
    public ApiResponse<AgentToolCatalogDto> catalog() {
        return ApiResponse.ok(agentConfigService.catalog());
    }

    @GetMapping
    public ApiResponse<List<AgentConfigDto>> list() {
        return ApiResponse.ok(agentConfigService.list());
    }

    @PostMapping
    public ApiResponse<AgentConfigDto> create(@RequestBody AgentConfigCreateRequest request) {
        return ApiResponse.ok(agentConfigService.create(request));
    }

    @GetMapping("/{code}")
    public ApiResponse<AgentConfigDto> get(@PathVariable("code") String code) {
        return ApiResponse.ok(agentConfigService.get(code));
    }

    @PutMapping("/{code}")
    public ApiResponse<AgentConfigDto> update(
            @PathVariable("code") String code,
            @RequestBody AgentConfigUpdateRequest request
    ) {
        return ApiResponse.ok(agentConfigService.update(code, request));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> delete(@PathVariable("code") String code) {
        agentConfigService.delete(code);
        return ApiResponse.ok(null);
    }

    @PostMapping("/reload")
    public ApiResponse<Map<String, Object>> reload() {
        agentConfigService.reload();
        return ApiResponse.ok(Map.of("reloaded", true));
    }

    @PostMapping("/{code}/versions/{version}/publish")
    public ApiResponse<AgentVersionDto> publishVersion(@PathVariable String code, @PathVariable int version,
                                                       @RequestBody PublishAgentRequest request) {
        if (publicationService == null) throw new IllegalStateException("publication service unavailable");
        String evaluationVersion = request == null ? "manual" : request.evaluationVersion();
        return ApiResponse.ok(publicationService.publish(code, new PublishAgentRequest(version, evaluationVersion), "admin"));
    }
}
