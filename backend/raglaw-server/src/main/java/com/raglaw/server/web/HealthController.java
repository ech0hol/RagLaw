package com.raglaw.server.web;

import com.raglaw.agentadmin.AgentAdminModule;
import com.raglaw.agentscope.AgentScopeModule;
import com.raglaw.chat.ChatModule;
import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.RagModule;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.service.EmbeddingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final RagProperties ragProperties;
    private final Optional<EmbeddingService> embeddingService;

    public HealthController(
            RagProperties ragProperties,
            ObjectProvider<EmbeddingService> embeddingService
    ) {
        this.ragProperties = ragProperties;
        this.embeddingService = Optional.ofNullable(embeddingService.getIfAvailable());
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> rag = new LinkedHashMap<>();
        rag.put("postgresEnabled", ragProperties.getPostgres().isEnabled());
        rag.put("embeddingConfigured", ragProperties.getEmbedding().isEnabled());
        rag.put("embeddingReady", embeddingService.map(EmbeddingService::isEnabled).orElse(false));
        rag.put(
                "hybridRetrievalReady",
                ragProperties.getPostgres().isEnabled()
                        && embeddingService.map(EmbeddingService::isEnabled).orElse(false)
        );

        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "raglaw-server",
                "modules", List.of(
                        ChatModule.NAME,
                        AgentScopeModule.NAME,
                        AgentAdminModule.NAME,
                        RagModule.NAME
                ),
                "rag", rag
        ));
    }
}
