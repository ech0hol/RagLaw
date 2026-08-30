package com.raglaw.server.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.LoginRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@RagLawIntegrationTest
@Sql(scripts = "/test-data/rag-eval-categories.sql")
class DocumentUploadBatchIT {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";
    private static final String CATEGORY_ID = "cat_l3_statute_civil_labor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadBatchReturnsTwoDocumentsWithSyncMode() throws Exception {
        String token = loginToken();
        MockMultipartFile fileOne = new MockMultipartFile(
                "files",
                "statute-a.md",
                "text/markdown",
                "# 法规 A\n\n劳动合同解除。".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile fileTwo = new MockMultipartFile(
                "files",
                "statute-b.md",
                "text/markdown",
                "# 法规 B\n\n工伤赔偿标准。".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult upload = mockMvc.perform(multipart("/api/v1/admin/documents/upload-batch")
                        .file(fileOne)
                        .file(fileTwo)
                        .param("categoryId", CATEGORY_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.ingestMode").value("SYNC"))
                .andReturn();

        var items = objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data")
                .path("items");
        assertThat(items.get(0).path("title").asText()).isNotBlank();
        assertThat(items.get(1).path("title").asText()).isNotBlank();
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
