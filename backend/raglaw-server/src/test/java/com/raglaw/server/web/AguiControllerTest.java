package com.raglaw.server.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raglaw.server.RagLawIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.server.auth.dto.LoginRequest;
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

    @Test
    void regenerateReplacesAssistantWithoutAppendingDuplicate() throws Exception {
        String token = loginToken();

        MvcResult createConv = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String conversationId = objectMapper.readTree(createConv.getResponse().getContentAsString())
                .path("data").path("id").asText();

        Map<String, Object> firstRun = Map.of(
                "conversationId", conversationId,
                "message", "劳动合同解除条件？"
        );
        MvcResult first = mockMvc.perform(post("/api/v1/agui/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRun)))
                .andExpect(request().asyncStarted())
                .andReturn();
        first.getAsyncResult(10_000L);

        Map<String, Object> regenerate = Map.of(
                "conversationId", conversationId,
                "message", "",
                "regenerate", true
        );
        MvcResult second = mockMvc.perform(post("/api/v1/agui/run")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regenerate)))
                .andExpect(request().asyncStarted())
                .andReturn();
        second.getAsyncResult(10_000L);

        MvcResult messages = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/v1/conversations/" + conversationId + "/messages")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        int assistantCount = objectMapper.readTree(messages.getResponse().getContentAsString())
                .path("data")
                .findValues("role")
                .stream()
                .map(node -> node.asText())
                .filter(role -> "assistant".equals(role))
                .toList()
                .size();
        assertThat(assistantCount).isEqualTo(1);
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
