package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.agentadmin.registry.AgentVersionRegistry;
import com.raglaw.agentscope.config.AgentscopeMcpProperties;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.runtime.AgentRunFactory;
import com.raglaw.agentscope.runtime.AgentRunSession;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.agent.RuntimeContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Invokes the exact published expert/version frozen in a workflow manifest. */
@Component
public class AgentScopeWorkflowAgentInvoker implements WorkflowAgentInvoker {
    private static final String RAG_SEARCH = "rag_search";
    private static final String TAVILY_SEARCH = "tavily-search";

    private final AgentRunFactory agentRunFactory;
    private final AgentVersionRegistry registry;
    private final AgentscopeLlmProperties llmProperties;
    private final AgentscopeMcpProperties mcpProperties;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    public AgentScopeWorkflowAgentInvoker(AgentRunFactory agentRunFactory, AgentVersionRegistry registry,
                                          AgentscopeLlmProperties llmProperties, Environment environment,
                                          ObjectMapper objectMapper, AgentscopeMcpProperties mcpProperties) {
        this.agentRunFactory = agentRunFactory;
        this.registry = registry;
        this.llmProperties = llmProperties;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.mcpProperties = mcpProperties;
    }

    @Override
    public AgentInvocationResult invoke(ResolvedWorkflowNode node, WorkflowContextView context,
                                       String prompt, WorkflowExecutionContext executionContext) throws Exception {
        AgentVersionSnapshot snapshot = registry.get(node.agentCode(), node.agentVersion());
        if (snapshot == null || !isUsableFrozenVersion(snapshot.status())) {
            throw new IllegalStateException("frozen expert version unavailable: " + node.agentCode() + "@v" + node.agentVersion());
        }
        if (!snapshot.toolPolicy().toolNames().containsAll(node.effectiveTools())) {
            throw new IllegalArgumentException("tool policy does not permit frozen node tools");
        }
        validateConfiguredRuntimeTools(node);
        if (llmProperties.isMock()) {
            return new AgentInvocationResult(
                    "模拟工作流结果：" + node.agentCode() + "@v" + node.agentVersion(),
                    List.of(), Map.of("mode", "mock", "agentCode", node.agentCode(), "agentVersion", node.agentVersion()));
        }

        String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY missing for workflow AgentScope execution");
        }
        ExpertContext expert = new ExpertContext(
                node.agentCode(), node.agentCode(), snapshot.systemPrompt(), snapshot.knowledgeScopes(),
                node.agentCode(), "workflow:" + node.nodeCode(), false, false, null, false,
                List.copyOf(node.effectiveTools()), snapshot.mcpServers());
        AgentRunSession session = new AgentRunSession(executionContext.runId() + ":" + node.nodeCode());
        session.setTraceId(executionContext.traceId());
        session.setUserMessage(prompt);
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .sessionId(session.taskId())
                .put(ExpertContext.class, expert)
                .put(AgentRunSession.class, session)
                .build();

        StringBuilder answer = new StringBuilder();
        try (ReActAgent agent = agentRunFactory.build(expert, snapshot.model(), apiKey)) {
            agent.streamEvents(new UserMessage(prompt), runtimeContext)
                    .doOnNext(event -> collect(event, answer, agent, runtimeContext, executionContext))
                    .blockLast();
        }
        if (answer.isEmpty()) throw new IllegalStateException("workflow AgentScope returned empty answer");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("mode", "agentscope");
        metadata.put("agentCode", node.agentCode());
        metadata.put("agentVersion", node.agentVersion());
        metadata.put("toolCount", node.effectiveTools().size());
        metadata.put("promptBytes", objectMapper.writeValueAsBytes(prompt).length);
        return new AgentInvocationResult(
                answer.toString(), session.hits().stream().map(hit -> hit.chunkId()).filter(id -> id != null && !id.isBlank()).toList(), metadata);
    }

    private static boolean isUsableFrozenVersion(AgentPublishStatus status) {
        return status == AgentPublishStatus.PUBLISHED
                || status == AgentPublishStatus.DEPRECATED
                || status == AgentPublishStatus.DISABLED;
    }

    private void validateConfiguredRuntimeTools(ResolvedWorkflowNode node) {
        for (String tool : node.effectiveTools()) {
            if (RAG_SEARCH.equals(tool)) continue;
            if (TAVILY_SEARCH.equals(tool)
                    && mcpProperties != null
                    && mcpProperties.isEnabled()
                    && mcpProperties.getApiKey() != null
                    && !mcpProperties.getApiKey().isBlank()
                    && mcpProperties.getAllowedTools() != null
                    && mcpProperties.getAllowedTools().contains(TAVILY_SEARCH)) {
                continue;
            }
            throw new IllegalArgumentException("runtime does not expose frozen node tool: " + tool);
        }
    }

    private void collect(AgentEvent event, StringBuilder answer, ReActAgent agent,
                         RuntimeContext runtimeContext, WorkflowExecutionContext executionContext) {
        if (executionContext.cancelled()) {
            agent.interrupt(runtimeContext);
            return;
        }
        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA) {
            String delta = ((TextBlockDeltaEvent) event).getDelta();
            if (delta != null) answer.append(delta);
        } else if (event.getType() == AgentEventType.AGENT_RESULT && answer.isEmpty()) {
            String result = ((AgentResultEvent) event).getResult().getTextContent();
            if (result != null) answer.append(result);
        }
    }
}
