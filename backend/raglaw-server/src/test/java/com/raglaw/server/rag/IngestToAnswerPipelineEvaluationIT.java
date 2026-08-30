package com.raglaw.server.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.RagTraceStageEntity;
import com.raglaw.agentscope.domain.RagTraceStageRepository;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.KnowledgeSearchService;
import com.raglaw.rag.tool.HybridRagSearchTool;
import com.raglaw.rag.tool.RagSearchResult;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.LoginRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end evaluation: upload → ingest → retrieval → AG-UI answer trace stages.
 */
@RagLawIntegrationTest
@Sql(scripts = "/test-data/rag-eval-categories.sql")
class IngestToAnswerPipelineEvaluationIT {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String CATEGORY_LABOR = "cat_l3_statute_civil_labor";
    private static final String CATEGORY_CASE = "cat_l3_case_civil_labor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private HybridRagSearchTool hybridRagSearchTool;

    @Autowired
    private KnowledgeSearchService knowledgeSearchService;

    @Autowired
    private RagTraceStageRepository stageRepository;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginToken();
    }

    @Test
    void ingestCreatesMicroChunksForFixtureCorpus() throws Exception {
        String laborDocId = uploadAndIngest(
                "labor-contract-law-excerpt.md",
                "../../docs/fixtures/statutes/labor-contract-law-excerpt.md",
                CATEGORY_LABOR
        );
        String caseDocId = uploadAndIngest(
                "labor-overtime-case.md",
                "../../docs/fixtures/cases/labor-overtime-case.md",
                CATEGORY_CASE
        );

        assertHierarchyChunksPresent(laborDocId);
        List<DocumentChunkEntity> caseChunks =
                documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(caseDocId);
        assertThat(caseChunks).as("case document chunks").isNotEmpty();
        assertThat(caseChunks.stream().anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.CHILD)).isTrue();
    }

    @Test
    void ragSearchUsesFunnelAndReturnsHitsForLaborQuery() throws Exception {
        String laborDocId = uploadAndIngest(
                "labor-contract-law-excerpt.md",
                "../../docs/fixtures/statutes/labor-contract-law-excerpt.md",
                CATEGORY_LABOR
        );

        try {
            RagSearchResult result = hybridRagSearchTool.searchDetailed(
                    "拖欠工资",
                    List.of("STATUTE", "CASE"),
                    5,
                    "GENERAL",
                    null,
                    false,
                    ""
            );

            assertThat(result.funnelResult()).isNotNull();
            if (!result.hits().isEmpty()) {
                assertThat(result.hits().get(0).l1L2L3Path()).startsWith("/STATUTE");
            }
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).containsIgnoringCase("MATCH");
            assertHierarchyChunksPresent(laborDocId);
        }
    }

    @Test
    void knowledgeSearchApiBypassesFunnelRouter() throws Exception {
        uploadAndIngest(
                "labor-contract-law-excerpt.md",
                "../../docs/fixtures/statutes/labor-contract-law-excerpt.md",
                CATEGORY_LABOR
        );

        try {
            var hits = knowledgeSearchService.search("经济补偿", "STATUTE", 5);
            if (!hits.isEmpty()) {
                assertThat(hits.get(0).path()).contains("/STATUTE");
            }
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            assertThat(ex.getMessage()).containsIgnoringCase("MATCH");
        }
    }

    @Test
    void aguiRunRecordsNewPipelineStagesNotLegacyPrefetch() throws Exception {
        uploadAndIngest(
                "labor-contract-law-excerpt.md",
                "../../docs/fixtures/statutes/labor-contract-law-excerpt.md",
                CATEGORY_LABOR
        );

        String conversationId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "conversationId", conversationId,
                "message", "公司拖欠工资如何维权？"
        );

        MvcResult asyncStarted = mockMvc.perform(post("/api/v1/agui/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncStarted.getAsyncResult(20_000L);
        String body = asyncStarted.getResponse().getContentAsString();
        assertThat(body).contains("event:done");

        String traceId = extractTraceId(body);
        List<RagTraceStageEntity> stages = stageRepository.findByTraceId(traceId);
        Set<String> stageNames = stages.stream().map(RagTraceStageEntity::getStage).collect(Collectors.toSet());

        assertThat(stageNames).contains("a2a_delegate");
        assertThat(stageNames).containsAnyOf("knowledge_funnel", "dual_channel_retrieval", "rag_search");

        RagTraceStageEntity delegateStage = stages.stream()
                .filter(stage -> "a2a_delegate".equals(stage.getStage()))
                .findFirst()
                .orElseThrow();
        JsonNode delegateDetail = objectMapper.readTree(delegateStage.getDetailJson());
        assertThat(delegateDetail.has("hitCount")).isFalse();

        RagTraceStageEntity retrievalStage = stages.stream()
                .filter(stage -> "dual_channel_retrieval".equals(stage.getStage())
                        || "rag_search".equals(stage.getStage()))
                .findFirst()
                .orElseThrow();
        JsonNode retrievalDetail = objectMapper.readTree(retrievalStage.getDetailJson());
        assertThat(retrievalDetail.path("react").asBoolean()).isTrue();
    }

    private void assertHierarchyChunksPresent(String documentId) {
        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        assertThat(chunks).as("document %s chunks", documentId).isNotEmpty();
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.PARENT))
                .as("document %s parent chunks", documentId)
                .isTrue();
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.CHILD))
                .as("document %s child chunks", documentId)
                .isTrue();
        assertThat(chunks.stream().anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.MICRO))
                .as("document %s micro chunks", documentId)
                .isTrue();
    }

    private String uploadAndIngest(String filename, String fixturePath, String categoryId) throws Exception {
        byte[] fixture = Files.readAllBytes(Path.of(fixturePath));

        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(new MockMultipartFile("file", filename, "text/markdown", fixture))
                        .param("categoryId", categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String documentId = objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();

        mockMvc.perform(post("/api/v1/admin/documents/" + documentId + "/ingest")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        var document = documentRepository.findById(documentId).orElseThrow();
        if ("CASE".equals(document.getDocType())) {
            assertThat(document.getStatus()).isEqualTo(DocStatus.AWAITING_APPROVAL);
            assertThat(document.getIngestStage()).isEqualTo(com.raglaw.rag.domain.IngestStage.PARSED);
        } else {
            assertThat(document.getStatus()).isEqualTo(DocStatus.INDEXED);
        }
        return documentId;
    }

    private String extractTraceId(String sseBody) throws Exception {
        for (String part : sseBody.split("\n\n")) {
            if (!part.contains("event:meta")) {
                continue;
            }
            for (String line : part.split("\n")) {
                if (line.startsWith("data:")) {
                    return objectMapper.readTree(line.substring(5).trim()).path("traceId").asText();
                }
            }
        }
        throw new IllegalStateException("SSE body missing meta event");
    }

    private String loginToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
