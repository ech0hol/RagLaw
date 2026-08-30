package com.raglaw.server.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.CreateUserRequest;
import com.raglaw.server.auth.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@RagLawIntegrationTest
class UserAdminControllerTest {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCreateAndListUsers() throws Exception {
        String token = loginToken();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                "lawyer-test@raglaw.local",
                                "lawyer-test-pass",
                                "测试律师",
                                "LAWYER"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("lawyer-test@raglaw.local"))
                .andExpect(jsonPath("$.data.role").value("LAWYER"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.email=='lawyer-test@raglaw.local')]").exists());
    }

    @Test
    void adminCanUpdateLawyerUser() throws Exception {
        String token = loginToken();
        createLawyer(token, "lawyer-patch@raglaw.local", "lawyer-patch-pass", "待编辑律师");
        String userId = findUserId(token, "lawyer-patch@raglaw.local");

        mockMvc.perform(patch("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"已更新律师\",\"enabled\":false,\"role\":\"LAWYER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.displayName").value("已更新律师"))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void adminCanPromoteLawyerToAdmin() throws Exception {
        String token = loginToken();
        createLawyer(token, "lawyer-promote@raglaw.local", "lawyer-promote-pass", "待晋升律师");
        String userId = findUserId(token, "lawyer-promote@raglaw.local");

        mockMvc.perform(patch("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"待晋升律师\",\"enabled\":true,\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void adminCanUpdateOwnDisplayNameWithoutChangingRole() throws Exception {
        String token = loginToken();
        String adminId = findUserId(token, ADMIN_EMAIL);

        mockMvc.perform(patch("/api/v1/admin/users/" + adminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"系统管理员（已更新）\",\"enabled\":true,\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("系统管理员（已更新）"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void adminCanDeleteLawyerUser() throws Exception {
        String token = loginToken();
        createLawyer(token, "lawyer-delete@raglaw.local", "lawyer-delete-pass", "待删除律师");
        String userId = findUserId(token, "lawyer-delete@raglaw.local");

        mockMvc.perform(delete("/api/v1/admin/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.email=='lawyer-delete@raglaw.local')]").doesNotExist());
    }

    @Test
    void cannotDeleteSelf() throws Exception {
        String token = loginToken();
        String adminId = findUserId(token, ADMIN_EMAIL);

        mockMvc.perform(delete("/api/v1/admin/users/" + adminId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void cannotDemoteLastEnabledAdmin() throws Exception {
        String token = loginToken();
        String adminId = findUserId(token, ADMIN_EMAIL);

        mockMvc.perform(patch("/api/v1/admin/users/" + adminId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"系统管理员\",\"enabled\":true,\"role\":\"LAWYER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void createLawyer(String token, String email, String password, String displayName) throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                email,
                                password,
                                displayName,
                                "LAWYER"
                        ))))
                .andExpect(status().isOk());
    }

    private String findUserId(String token, String email) throws Exception {
        String listBody = mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (JsonNode node : objectMapper.readTree(listBody).path("data")) {
            if (email.equals(node.path("email").asText())) {
                return node.path("id").asText();
            }
        }
        throw new IllegalStateException("User not found: " + email);
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
