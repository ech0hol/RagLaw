package com.raglaw.agentadmin.web;

import com.raglaw.agentadmin.dto.AgentVersionDto;
import com.raglaw.agentadmin.dto.AgentEvaluationSummaryDto;
import com.raglaw.agentadmin.dto.AgentValidationDto;
import com.raglaw.agentadmin.dto.CreateAgentVersionRequest;
import com.raglaw.agentadmin.dto.PublishAgentRequest;
import com.raglaw.agentadmin.dto.TransitionAgentVersionRequest;
import com.raglaw.agentadmin.service.AgentPublicationService;
import com.raglaw.common.api.ApiResponse;
import com.raglaw.common.api.ErrorCodes;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.security.Principal;

@RestController
@RequestMapping("/api/v1/admin/agent-versions")
public class AgentVersionAdminController {
    private final AgentPublicationService publicationService;
    public AgentVersionAdminController(AgentPublicationService publicationService) { this.publicationService = publicationService; }

    @PostMapping("/{agentCode}")
    public ApiResponse<AgentVersionDto> create(@PathVariable("agentCode") String agentCode, @RequestBody CreateAgentVersionRequest request, Principal principal) {
        return ApiResponse.ok(publicationService.createVersion(agentCode, request, actor(principal)));
    }

    @PostMapping("/{agentCode}/{version}/validate")
    public ApiResponse<AgentValidationDto> validate(@PathVariable("agentCode") String agentCode, @PathVariable("version") int version) {
        return ApiResponse.ok(publicationService.validate(agentCode, version));
    }

    @PostMapping("/{agentCode}/{version}/shadow")
    public ApiResponse<AgentVersionDto> shadow(@PathVariable("agentCode") String agentCode, @PathVariable("version") int version) {
        return ApiResponse.ok(publicationService.enterShadow(agentCode, version));
    }

    @PostMapping("/{agentCode}/{version}/transition")
    public ApiResponse<AgentVersionDto> transition(@PathVariable("agentCode") String agentCode,
                                                   @PathVariable("version") int version,
                                                   @RequestBody TransitionAgentVersionRequest request,
                                                   Principal principal) {
        return ApiResponse.ok(publicationService.transitionTo(agentCode, version, request == null ? null : request.targetStatus(), actor(principal)));
    }

    @PostMapping("/{agentCode}/publish")
    public ApiResponse<AgentVersionDto> publish(@PathVariable("agentCode") String agentCode, @RequestBody PublishAgentRequest request, Principal principal) {
        return ApiResponse.ok(publicationService.publish(agentCode, request, actor(principal)));
    }

    @GetMapping("/published")
    public ApiResponse<List<AgentVersionDto>> published() {
        return ApiResponse.ok(publicationService.publishedCandidates().stream()
                .map(snapshot -> new AgentVersionDto(snapshot.agentCode(), snapshot.version(), snapshot.status(),
                        snapshot.evaluationScore(), snapshot.configChecksum(), null, null, null, null, null, null)).toList());
    }

    @PostMapping("/{agentCode}/{version}/deprecate")
    public ApiResponse<AgentVersionDto> deprecate(@PathVariable("agentCode") String agentCode, @PathVariable("version") int version, Principal principal) {
        return ApiResponse.ok(publicationService.deprecate(agentCode, version, actor(principal)));
    }

    @PostMapping("/{agentCode}/{version}/disable")
    public ApiResponse<AgentVersionDto> disable(@PathVariable("agentCode") String agentCode, @PathVariable("version") int version, Principal principal) {
        return ApiResponse.ok(publicationService.disable(agentCode, version, actor(principal)));
    }

    @PostMapping("/{agentCode}/{version}/archive")
    public ApiResponse<AgentVersionDto> archive(@PathVariable("agentCode") String agentCode, @PathVariable("version") int version, Principal principal) {
        return ApiResponse.ok(publicationService.archive(agentCode, version, actor(principal)));
    }

    @GetMapping("/{agentCode}/history")
    public ApiResponse<List<AgentVersionDto>> history(@PathVariable("agentCode") String agentCode) {
        return ApiResponse.ok(publicationService.history(agentCode));
    }

    @GetMapping("/{agentCode}/{version}/evaluation-summary")
    public ApiResponse<AgentEvaluationSummaryDto> evaluationSummary(@PathVariable("agentCode") String agentCode, @PathVariable("version") int version) {
        return ApiResponse.ok(publicationService.evaluationSummary(agentCode, version));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTransition(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ErrorCodes.AGENT_VERSION_CONFLICT, exception.getMessage()));
    }

    private static String actor(Principal principal) {
        return principal == null || principal.getName() == null || principal.getName().isBlank() ? "admin" : principal.getName();
    }
}
