package com.raglaw.server.chat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.chat.dto.AppendMessageRequest;
import com.raglaw.chat.dto.CreateConversationRequest;
import com.raglaw.chat.dto.UpdateConversationTitleRequest;
import com.raglaw.server.auth.dto.LoginRequest;
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
class ConversationControllerTest {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void conversationLifecycle() throws Exception {
        String token = login();

        MvcResult create = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateConversationRequest("GENERAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("新对话"))
                .andReturn();

        String conversationId = objectMapper.readTree(create.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();

        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AppendMessageRequest("user", "劳动合同解除的赔偿标准是什么？", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("user"));

        mockMvc.perform(get("/api/v1/conversations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(conversationId))
                .andExpect(jsonPath("$.data[0].title").value("劳动合同解除的赔偿标准是什么？"));

        mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/v1/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateConversationTitleRequest("自定义标题"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("自定义标题"));

        mockMvc.perform(delete("/api/v1/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void conversationsRequireAuth() throws Exception {
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
