package com.raglaw.server.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.LoginRequest;
import java.util.List;
import java.util.Map;
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
class TraceAdminControllerSecurityTest {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagTraceRepository ragTraceRepository;

    @Test
    void deleteWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/traces/{traceId}", "trace-missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("未登录"));
    }

    @Test
    void deleteWithAdminTokenSucceeds() throws Exception {
        String traceId = UUID.randomUUID().toString();
        ragTraceRepository.save(new RagTraceEntity(
                traceId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "测试删除",
                "GENERAL"
        ));

        mockMvc.perform(delete("/api/v1/admin/traces/{traceId}", traceId)
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(ragTraceRepository.existsById(traceId)).isFalse();
    }

    @Test
    void batchDeleteWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/admin/traces/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("traceIds", List.of("trace-1")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("未登录"));
    }

    @Test
    void batchDeleteWithAdminTokenSucceeds() throws Exception {
        String traceId = UUID.randomUUID().toString();
        ragTraceRepository.save(new RagTraceEntity(
                traceId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "批量删除测试",
                "CONTRACT"
        ));

        mockMvc.perform(post("/api/v1/admin/traces/batch-delete")
                        .header("Authorization", "Bearer " + loginToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("traceIds", List.of(traceId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deleted").value(1));

        assertThat(ragTraceRepository.existsById(traceId)).isFalse();
    }

    @Test
    void listFiltersByAgentCodeAndQuery() throws Exception {
        String generalId = UUID.randomUUID().toString();
        String contractId = UUID.randomUUID().toString();
        ragTraceRepository.save(new RagTraceEntity(
                generalId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "通用法律咨询",
                "GENERAL"
        ));
        ragTraceRepository.save(new RagTraceEntity(
                contractId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "批量删除测试",
                "CONTRACT"
        ));

        String token = loginToken();

        mockMvc.perform(get("/api/v1/admin/traces")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("agentCode", "CONTRACT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(contractId));

        mockMvc.perform(get("/api/v1/admin/traces")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("q", "批量")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(contractId));

        mockMvc.perform(get("/api/v1/admin/traces")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("agentCode", "CONTRACT")
                        .param("q", "批量")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(contractId));

        mockMvc.perform(get("/api/v1/admin/traces")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("agentCode", "GENERAL")
                        .param("q", "批量")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
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
