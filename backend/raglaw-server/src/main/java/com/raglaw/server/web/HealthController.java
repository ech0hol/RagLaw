package com.raglaw.server.web;

import com.raglaw.agentadmin.AgentAdminModule;
import com.raglaw.agentscope.AgentScopeModule;
import com.raglaw.chat.ChatModule;
import com.raglaw.common.api.ApiResponse;
import com.raglaw.rag.RagModule;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.repository.DocumentRepository;
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
    private final DocumentRepository documentRepository;
    private final boolean llmMock;
    private final String rabbitParseQueue;
    private final String rabbitIndexQueue;

    public HealthController(
            RagProperties ragProperties,
            ObjectProvider<EmbeddingService> embeddingService,
            DocumentRepository documentRepository,
            @Value("${raglaw.llm.mock:false}") boolean llmMock,
            @Value("${raglaw.rag.rabbit.parse-queue:raglaw.parse}") String rabbitParseQueue,
            @Value("${raglaw.rag.rabbit.index-queue:raglaw.index}") String rabbitIndexQueue
    ) {
        this.ragProperties = ragProperties;
        this.embeddingService = Optional.ofNullable(embeddingService.getIfAvailable());
        this.documentRepository = documentRepository;
        this.llmMock = llmMock;
        this.rabbitParseQueue = rabbitParseQueue;
        this.rabbitIndexQueue = rabbitIndexQueue;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean embeddingReady = embeddingService.map(EmbeddingService::isEnabled).orElse(false);
        boolean elasticsearchEnabled = ragProperties.getElasticsearch().isEnabled();
        Map<String, Object> rag = new LinkedHashMap<>();
        rag.put("elasticsearchEnabled", elasticsearchEnabled);
        rag.put("embeddingConfigured", ragProperties.getEmbedding().isEnabled());
        rag.put("embeddingReady", embeddingReady);
        rag.put("embeddingMock", embeddingService.map(EmbeddingService::isMockMode).orElse(false));
        rag.put(
                "hybridRetrievalReady",
                elasticsearchEnabled && embeddingReady
        );
        if (elasticsearchEnabled && embeddingReady) {
            long missingEsSync = documentRepository.countByStatusAndDocTypeNotAndIndexVersion(
                    DocStatus.INDEXED,
                    "CONTRACT",
                    0L
            );
            rag.put("indexedDocumentsMissingEsSync", missingEsSync);
            rag.put("elasticsearchIndexSyncReady", missingEsSync == 0L);
        } else {
            rag.put("indexedDocumentsMissingEsSync", null);
            rag.put("elasticsearchIndexSyncReady", false);
        }
        rag.put("minioEnabled", ragProperties.getMinio().isEnabled());
        rag.put("rabbitEnabled", ragProperties.getRabbit().isEnabled());
        if (ragProperties.getRabbit().isEnabled()) {
            rag.put("rabbitParseQueue", rabbitParseQueue);
            rag.put("rabbitIndexQueue", rabbitIndexQueue);
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
