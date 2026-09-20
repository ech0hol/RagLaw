package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolGrant;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.agentadmin.registry.AgentVersionRegistry;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.config.AgentscopeMcpProperties;
import com.raglaw.agentscope.runtime.AgentRunFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScopeWorkflowAgentInvokerTest {

    @Test
    void mockModeUsesTheFrozenPublishedVersionAndRejectsUnauthorizedTools() throws Exception {
        AgentVersionRegistry registry = mock(AgentVersionRegistry.class);
        AgentRunFactory agentRunFactory = mock(AgentRunFactory.class);
        Environment environment = mock(Environment.class);
        AgentscopeLlmProperties properties = new AgentscopeLlmProperties();
        properties.setMock(true);
        AgentVersionSnapshot snapshot = snapshot("statute-expert", 7, Set.of("rag_search"));
        when(registry.get("statute-expert", 7)).thenReturn(snapshot);

        AgentScopeWorkflowAgentInvoker invoker = new AgentScopeWorkflowAgentInvoker(
                agentRunFactory, registry, properties, environment, new ObjectMapper(), new AgentscopeMcpProperties());
        ResolvedWorkflowNode node = new ResolvedWorkflowNode(
                "statute", "STATUTE", "statute-expert", 7, Set.of("rag_search"), "", List.of());

        AgentInvocationResult result = invoker.invoke(
                node, null, "find the applicable statute", new WorkflowExecutionContext("run", "trace", "input", java.util.Map.of()));

        assertThat(result.answer()).contains("statute-expert", "v7");
        assertThat(result.metadata()).containsEntry("mode", "mock");

        ResolvedWorkflowNode unauthorized = new ResolvedWorkflowNode(
                "statute", "STATUTE", "statute-expert", 7, Set.of("tavily-search"), "", List.of());
        assertThatThrownBy(() -> invoker.invoke(
                unauthorized, null, "query", new WorkflowExecutionContext("run", "trace", "input", java.util.Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool policy");

        AgentVersionSnapshot unsupportedRuntime = snapshot("statute-expert", 8, Set.of("history_lookup"));
        when(registry.get("statute-expert", 8)).thenReturn(unsupportedRuntime);
        ResolvedWorkflowNode unavailableTool = new ResolvedWorkflowNode(
                "statute", "STATUTE", "statute-expert", 8, Set.of("history_lookup"), "", List.of());
        assertThatThrownBy(() -> invoker.invoke(
                unavailableTool, null, "query", new WorkflowExecutionContext("run", "trace", "input", java.util.Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime does not expose");
    }

    @Test
    void unavailableFrozenVersionFailsClosedBeforeAgentInvocation() {
        AgentVersionRegistry registry = mock(AgentVersionRegistry.class);
        when(registry.get("missing", 2)).thenReturn(null);
        AgentscopeLlmProperties properties = new AgentscopeLlmProperties();
        properties.setMock(true);
        AgentScopeWorkflowAgentInvoker invoker = new AgentScopeWorkflowAgentInvoker(
                mock(AgentRunFactory.class), registry, properties, mock(Environment.class), new ObjectMapper(), new AgentscopeMcpProperties());
        ResolvedWorkflowNode node = new ResolvedWorkflowNode(
                "node", "ROLE", "missing", 2, Set.of(), "", List.of());

        assertThatThrownBy(() -> invoker.invoke(
                node, null, "query", new WorkflowExecutionContext("run", "trace", "input", java.util.Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen expert version unavailable");
    }

    @Test
    void configuredButUnavailableMcpToolFailsClosedEvenInMockMode() {
        AgentVersionRegistry registry = mock(AgentVersionRegistry.class);
        AgentscopeLlmProperties properties = new AgentscopeLlmProperties();
        properties.setMock(true);
        when(registry.get("web-expert", 1)).thenReturn(snapshot("web-expert", 1, Set.of("tavily-search")));
        AgentScopeWorkflowAgentInvoker invoker = new AgentScopeWorkflowAgentInvoker(
                mock(AgentRunFactory.class), registry, properties, mock(Environment.class), new ObjectMapper(),
                new AgentscopeMcpProperties());
        ResolvedWorkflowNode node = new ResolvedWorkflowNode(
                "web", "WEB", "web-expert", 1, Set.of("tavily-search"), "", List.of());

        assertThatThrownBy(() -> invoker.invoke(
                node, null, "query", new WorkflowExecutionContext("run", "trace", "input", Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime does not expose");
    }

    @Test
    void frozenDeprecatedVersionRemainsExecutableForExistingWorkflow() throws Exception {
        AgentVersionRegistry registry = mock(AgentVersionRegistry.class);
        AgentscopeLlmProperties properties = new AgentscopeLlmProperties();
        properties.setMock(true);
        AgentVersionSnapshot deprecated = snapshot("labor-expert", 1, Set.of());
        // Rebuild the immutable snapshot with the lifecycle status used by a frozen manifest.
        deprecated = new AgentVersionSnapshot(deprecated.agentCode(), deprecated.version(), AgentPublishStatus.DEPRECATED,
                deprecated.model(), deprecated.systemPrompt(), deprecated.manifest(), deprecated.toolPolicy(), deprecated.skills(),
                deprecated.knowledgeScopes(), deprecated.mcpServers(), deprecated.evaluationScore(), deprecated.configChecksum());
        when(registry.get("labor-expert", 1)).thenReturn(deprecated);
        AgentScopeWorkflowAgentInvoker invoker = new AgentScopeWorkflowAgentInvoker(
                mock(AgentRunFactory.class), registry, properties, mock(Environment.class), new ObjectMapper(), new AgentscopeMcpProperties());
        ResolvedWorkflowNode node = new ResolvedWorkflowNode("node", "ROLE", "labor-expert", 1, Set.of(), "", List.of());

        assertThat(invoker.invoke(node, null, "query", new WorkflowExecutionContext("run", "trace", "input", Map.of())).text())
                .contains("labor-expert@v1");
    }

    private AgentVersionSnapshot snapshot(String code, int version, Set<String> tools) {
        AgentCapabilityManifest manifest = new AgentCapabilityManifest(
                Set.of("LABOR"), Set.of("STATUTE_RESEARCH"), Set.of("COMPLEX_LEGAL"),
                Set.of("LOW", "HIGH"), Set.of(), "answer");
        AgentToolPolicy policy = new AgentToolPolicy(
                tools.stream().map(tool -> new AgentToolGrant(tool, "READ_ONLY", false, Set.of("CASE"), 1000L)).toList(),
                Set.of());
        return new AgentVersionSnapshot(code, version, AgentPublishStatus.PUBLISHED, "dashscope:qwen-plus",
                "You are a statute expert.", manifest, policy, List.of(), List.of("labor"), List.of(), 0.95, "checksum");
    }
}
