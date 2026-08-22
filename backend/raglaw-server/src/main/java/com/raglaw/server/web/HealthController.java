package com.raglaw.server.web;

import com.raglaw.agentadmin.AgentAdminModule;
import com.raglaw.agentscope.AgentScopeModule;
import com.raglaw.chat.ChatModule;
import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.RagModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "raglaw-server",
                "modules", List.of(
                        ChatModule.NAME,
                        AgentScopeModule.NAME,
                        AgentAdminModule.NAME,
                        RagModule.NAME
                )
        ));
    }
}
