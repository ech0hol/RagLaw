package com.raglaw.agentadmin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.raglaw.agentadmin.service.AgentPublicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class AgentVersionAdminControllerTest {
    MockMvc mvc;
    @Mock AgentPublicationService publicationService;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new AgentVersionAdminController(publicationService)).build();
    }

    @Test
    void invalidLifecycleTransitionReturnsStableConflict() throws Exception {
        when(publicationService.enterShadow("LABOR", 1))
                .thenThrow(new IllegalStateException("Only validating versions can enter shadow"));

        mvc.perform(post("/api/v1/admin/agent-versions/LABOR/1/shadow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AGENT_VERSION_CONFLICT"));
    }
}
