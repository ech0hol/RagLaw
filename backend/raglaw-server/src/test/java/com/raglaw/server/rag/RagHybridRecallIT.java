package com.raglaw.server.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.tool.HybridRagSearchTool;
import com.raglaw.rag.tool.RagSearchResult;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.raglaw.server.auth.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hybrid recall via Elasticsearch BM25 (embedding disabled). Requires Docker.
 */
@SpringBootTest(properties = {
        "raglaw.seed.admin-password=admin-test-password",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class RagHybridRecallIT {

    private static final String CATEGORY_LABOR = "cat_l3_statute_civil_labor";
    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("raglaw")
            .withUsername("raglaw")
            .withPassword("raglaw");

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.15.0")
    ).withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("raglaw.rag.elasticsearch.enabled", () -> "true");
        registry.add("raglaw.rag.elasticsearch.uri", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
        registry.add("raglaw.rag.embedding.enabled", () -> "false");
        registry.add("raglaw.rag.outbox.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HybridRagSearchTool hybridRagSearchTool;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginToken();
    }

    @Test
    void ingestedLaborFixtureSupportsRecallBenchmark() throws Exception {
        uploadAndIngest(
                "labor-contract-law-excerpt.md",
                "../../docs/fixtures/statutes/labor-contract-law-excerpt.md"
        );

        boolean hasMicro = documentChunkRepository.findAll().stream()
                .anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.MICRO);
        assertThat(hasMicro).isTrue();

        try (InputStream input = Files.newInputStream(
                Path.of("../raglaw-rag/src/test/resources/recall-benchmark.json"))) {
            JsonNode queries = objectMapper.readTree(input).path("queries");
            for (JsonNode query : queries) {
                String text = query.path("text").asText();
                int minHitCount = query.path("minHitCount").asInt(1);
                String top1PathPrefix = query.path("top1PathPrefix").asText("");

                RagSearchResult result = hybridRagSearchTool.searchDetailed(
                        text,
                        List.of("STATUTE", "CASE"),
                        5,
                        "GENERAL",
                        null,
                        false,
                        "hybrid_recall_it"
                );

                assertThat(result.funnelResult())
                        .as("funnel for query: %s", text)
                        .isNotNull();
                assertThat(result.hits().size())
                        .as("hits for query: %s", text)
                        .isGreaterThanOrEqualTo(minHitCount);

                if (!top1PathPrefix.isBlank()) {
                    String path = result.hits().get(0).l1L2L3Path();
                    assertThat(path)
                            .as("top1 path for query: %s", text)
                            .startsWith(top1PathPrefix);
                }
            }
        }
    }

    private void uploadAndIngest(String filename, String fixturePath) throws Exception {
        byte[] fixture = Files.readAllBytes(Path.of(fixturePath));

        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(new MockMultipartFile("file", filename, "text/markdown", fixture))
                        .param("categoryId", CATEGORY_LABOR)
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
    }

    private String loginToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
