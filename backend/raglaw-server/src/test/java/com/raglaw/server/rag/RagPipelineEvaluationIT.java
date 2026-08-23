package com.raglaw.server.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.LoginRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@RagLawIntegrationTest
@Sql(scripts = "/test-data/rag-eval-categories.sql")
class RagPipelineEvaluationIT {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String CATEGORY_ID = "cat_l3_statute_civil_labor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Test
    void uploadIngestAndIndexDocument() throws Exception {
        String token = loginToken();
        byte[] fixture = Files.readAllBytes(
                Path.of("../../docs/fixtures/statutes/labor-contract-law-excerpt.md"));

        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/documents/upload")
                        .file(new MockMultipartFile("file", "labor-contract-law-excerpt.md", "text/markdown", fixture))
                        .param("categoryId", CATEGORY_ID)
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
        assertThat(document.getStatus()).isEqualTo(DocStatus.INDEXED);
        assertThat(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId)).isNotEmpty();

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));
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
