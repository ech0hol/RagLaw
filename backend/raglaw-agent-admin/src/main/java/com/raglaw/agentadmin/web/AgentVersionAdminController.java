package com.raglaw.agentadmin.web;

import com.raglaw.agentadmin.dto.AgentVersionDto;
import com.raglaw.agentadmin.dto.PublishAgentRequest;
import com.raglaw.agentadmin.service.AgentPublicationService;
import com.raglaw.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agent-versions")
public class AgentVersionAdminController {
    private final AgentPublicationService publicationService;
    public AgentVersionAdminController(AgentPublicationService publicationService) { this.publicationService = publicationService; }

    @PostMapping("/{agentCode}/publish")
    public ApiResponse<AgentVersionDto> publish(@PathVariable String agentCode, @RequestBody PublishAgentRequest request) {
        return ApiResponse.ok(publicationService.publish(agentCode, request, "admin"));
    }

    @GetMapping("/published")
    public ApiResponse<List<AgentVersionDto>> published() {
        return ApiResponse.ok(publicationService.publishedCandidates().stream()
                .map(snapshot -> new AgentVersionDto(snapshot.agentCode(), snapshot.version(), snapshot.status(),
                        snapshot.evaluationScore(), snapshot.configChecksum(), null, null, null)).toList());
    }
}
