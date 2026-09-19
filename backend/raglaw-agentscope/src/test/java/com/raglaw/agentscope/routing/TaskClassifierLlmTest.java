package com.raglaw.agentscope.routing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.agui.DashScopeClient;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

class TaskClassifierLlmTest {

    private DashScopeClient client;
    private Environment environment;
    private AgentscopeLlmProperties properties;
    private TaskClassifierLlm classifier;
    private RoutingRequest request;

    @BeforeEach
    void setUp() {
        client = mock(DashScopeClient.class);
        environment = mock(Environment.class);
        properties = new AgentscopeLlmProperties();
        when(environment.matchesProfiles("test")).thenReturn(false);
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn("fictional-key");
        classifier = new TaskClassifierLlm(client, new ObjectMapper(), properties, environment);
        request = new RoutingRequest("tenant-fictional", "user-fictional", "case-fictional",
                "conversation-fictional", "解除通知是否需要说明理由？", false, true);
    }

    @Test
    void parsesValidJsonAndMakesExactlyOneCall() throws Exception {
        when(client.completeChat(anyString(), anyString(), anyString(), anyString())).thenReturn(
                "{\"taskType\":\"DISPUTE_ANALYSIS\",\"riskSignals\":[\"MULTI_ISSUE_ANALYSIS\"],"
                        + "\"confidence\":0.86,\"missingMaterials\":[\"劳动合同\"],"
                        + "\"rationale\":\"fictional rationale\"}");

        TaskClassification result = classifier.classify(request);

        assertEquals(TaskType.DISPUTE_ANALYSIS, result.taskType());
        assertEquals(java.util.Set.of(RiskSignal.MULTI_ISSUE_ANALYSIS), result.riskSignals());
        assertEquals(0.86, result.confidence());
        assertEquals("task-classifier-v1", result.promptVersion());
        assertEquals("qwen-turbo", result.modelVersion());
        verify(client, times(1)).completeChat(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void parsesSingleMarkdownFence() throws Exception {
        when(client.completeChat(anyString(), anyString(), anyString(), anyString())).thenReturn(
                "```json\n{\"taskType\":\"STATUTE_LOOKUP\",\"riskSignals\":[],"
                        + "\"confidence\":0.5,\"missingMaterials\":[],\"rationale\":\"ambiguous\"}\n```");
        assertEquals(TaskType.STATUTE_LOOKUP, classifier.classify(request).taskType());
    }

    @Test
    void rejectsUnsafeOutputToConservativeFallback() throws Exception {
        String[] outputs = {
                "{\"taskType\":\"NOT_A_TASK\",\"riskSignals\":[],\"confidence\":0.5,\"missingMaterials\":[],\"rationale\":\"x\"}",
                "{\"taskType\":\"STATUTE_LOOKUP\",\"riskSignals\":[],\"confidence\":0.5,\"rationale\":\"x\"}",
                "{\"taskType\":\"STATUTE_LOOKUP\",\"riskSignals\":[],\"confidence\":1.1,\"missingMaterials\":[],\"rationale\":\"x\"}",
                "{\"taskType\":\"STATUTE_LOOKUP\",\"riskSignals\":[\"IMMINENT_DEADLINE\",\"IMMINENT_DEADLINE\"],\"confidence\":0.5,\"missingMaterials\":[],\"rationale\":\"x\"}",
                "not json"
        };
        for (String output : outputs) {
            reset(client);
            when(client.completeChat(anyString(), anyString(), anyString(), anyString())).thenReturn(output);
            TaskClassification result = classifier.classify(request);
            assertEquals(TaskType.DISPUTE_ANALYSIS, result.taskType());
            assertEquals(java.util.Set.of(RiskSignal.MISSING_CORE_MATERIAL), result.riskSignals());
            assertEquals(0.0, result.confidence());
            assertEquals(java.util.List.of("classifier_output"), result.missingMaterials());
            assertTrue(result.rationale().startsWith("classifier_failure:"));
        }
    }

    @Test
    void timeoutOrClientExceptionFallsBackWithoutThrowing() throws Exception {
        when(client.completeChat(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new java.net.http.HttpTimeoutException("fictional timeout"));
        TaskClassification result = assertDoesNotThrow(() -> classifier.classify(request));
        assertEquals(0.0, result.confidence());
        assertTrue(result.rationale().startsWith("classifier_failure:"));
    }

    @Test
    void promptContainsControlledLabelsAndNonAuthorizationStatement() {
        String prompt = TaskClassifierPrompt.systemPrompt();
        assertTrue(prompt.contains("DISPUTE_ANALYSIS"));
        assertTrue(prompt.contains("MISSING_CORE_MATERIAL"));
        assertTrue(prompt.contains("不授权执行或调用工具"));
        assertTrue(prompt.contains("JSON"));
        assertTrue(prompt.contains(request.query()) || TaskClassifierPrompt.userMessage(request).contains(request.query()));
    }

    @Test
    void missingApiKeyFailsFastWithoutCallingClient() {
        when(environment.getProperty("DASHSCOPE_API_KEY")).thenReturn(" ");
        TaskClassification result = classifier.classify(request);
        assertTrue(result.rationale().startsWith("classifier_failure:"));
        verifyNoInteractions(client);
    }
}
