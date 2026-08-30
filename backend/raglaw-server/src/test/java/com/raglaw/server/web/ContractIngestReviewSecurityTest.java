package com.raglaw.server.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.LoginRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@RagLawIntegrationTest
@SpringBootTest(properties = {
        "raglaw.seed.admin-password=admin-test-password"
})
class ContractIngestReviewSecurityTest {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ingestReviewWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/{documentId}/ingest-review", "doc-missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("未登录"));
    }

    @Test
    void ingestReviewWithAdminTokenIsNotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/{documentId}/ingest-review", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void parseReviewWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/{documentId}/parse-review", "doc-missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("未登录"));
    }

    @Test
    void parseReviewWithAdminTokenIsNotUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/contracts/{documentId}/parse-review", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private String loginToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
