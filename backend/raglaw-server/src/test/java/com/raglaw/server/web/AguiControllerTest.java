package com.raglaw.server.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.server.auth.dto.LoginRequest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AguiControllerTest {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagTraceRepository ragTraceRepository;

    @Test
    void runStreamsSseWithMockLlm() throws Exception {
        String token = loginToken();

        String conversationId = UUID.randomUUID().toString();
        Map<String, Object> payload = Map.of(
                "conversationId", conversationId,
                "message", "劳动合同解除条件？"
        );

        long tracesBefore = ragTraceRepository.count();

        MvcResult asyncStarted = mockMvc.perform(post("/api/v1/agui/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncStarted.getAsyncResult(10_000L);
        String body = asyncStarted.getResponse().getContentAsString();
        assertThat(body).contains("event:meta");
        assertThat(body).contains("event:text");
        assertThat(body).contains("event:done");
        assertThat(body).contains("GENERAL");
        assertThat(ragTraceRepository.count()).isGreaterThan(tracesBefore);
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
