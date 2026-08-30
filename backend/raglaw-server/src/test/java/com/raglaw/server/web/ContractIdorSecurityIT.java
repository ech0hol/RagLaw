package com.raglaw.server.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.server.RagLawIntegrationTest;
import com.raglaw.server.auth.dto.CreateUserRequest;
import com.raglaw.server.auth.dto.LoginRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@RagLawIntegrationTest
@SpringBootTest(properties = {
        "raglaw.seed.admin-password=admin-test-password"
})
@Sql(scripts = "/test-data/rag-eval-categories.sql")
class ContractIdorSecurityIT {

    private static final String ADMIN_EMAIL = "admin@raglaw.local";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void nonOwnerCannotReadContractText() throws Exception {
        String adminToken = loginToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        String lawyerToken = createLawyerAndLogin("lawyer-idor@raglaw.local", "lawyer-idor-pass");
        String documentId = uploadContract(adminToken);

        mockMvc.perform(get("/api/v1/contracts/{documentId}/text", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void nonOwnerCannotTriggerIngestReview() throws Exception {
        String adminToken = loginToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        String lawyerToken = createLawyerAndLogin("lawyer-idor2@raglaw.local", "lawyer-idor2-pass");
        String documentId = uploadContract(adminToken);

        mockMvc.perform(post("/api/v1/contracts/{documentId}/ingest-review", documentId)
                        .header("Authorization", "Bearer " + lawyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String uploadContract(String token) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract-idor.txt",
                "text/plain",
                "甲方与乙方签订服务合同。".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult upload = mockMvc.perform(multipart("/api/v1/contracts/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(upload.getResponse().getContentAsString())
                .path("data")
                .path("id")
                .asText();
    }

    private String createLawyerAndLogin(String email, String password) throws Exception {
        String adminToken = loginToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateUserRequest(
                                email,
                                password,
                                "IDOR测试律师",
                                "LAWYER"
                        ))))
                .andExpect(status().isOk());
        return loginToken(email, password);
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }
}
