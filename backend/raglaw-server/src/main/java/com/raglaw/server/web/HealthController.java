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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final RagProperties ragProperties;
    private final Optional<EmbeddingService> embeddingService;
    private final boolean llmMock;

    public HealthController(
            RagProperties ragProperties,
            ObjectProvider<EmbeddingService> embeddingService,
            @Value("${raglaw.llm.mock:false}") boolean llmMock
    ) {
        this.ragProperties = ragProperties;
        this.embeddingService = Optional.ofNullable(embeddingService.getIfAvailable());
        this.llmMock = llmMock;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean embeddingReady = embeddingService.map(EmbeddingService::isEnabled).orElse(false);
        Map<String, Object> rag = new LinkedHashMap<>();
        rag.put("postgresEnabled", ragProperties.getPostgres().isEnabled());
        rag.put("embeddingConfigured", ragProperties.getEmbedding().isEnabled());
        rag.put("embeddingReady", embeddingReady);
        rag.put("embeddingMock", embeddingService.map(EmbeddingService::isMockMode).orElse(false));
        rag.put(
                "hybridRetrievalReady",
                ragProperties.getPostgres().isEnabled() && embeddingReady
        );
        rag.put("minioEnabled", ragProperties.getMinio().isEnabled());
        rag.put("rabbitEnabled", ragProperties.getRabbit().isEnabled());
        if (ragProperties.getRabbit().isEnabled()) {
            rag.put("rabbitParseQueue", ragProperties.getRabbit().getParseQueue());
            rag.put("rabbitIndexQueue", ragProperties.getRabbit().getIndexQueue());
        }

        return ApiResponse.ok(Map.of(
                "status", "UP",
                "service", "raglaw-server",
                "modules", List.of(
                        ChatModule.NAME,
                        AgentScopeModule.NAME,
                        AgentAdminModule.NAME,
                        RagModule.NAME
                ),
                "rag", rag,
                "llmMock", llmMock
        ));
    }
}
