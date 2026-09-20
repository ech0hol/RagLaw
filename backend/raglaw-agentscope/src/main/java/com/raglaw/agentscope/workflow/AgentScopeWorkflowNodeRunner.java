package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds a DAG node to the immutable expert/version/tool selection in a manifest.
 * It is created per workflow run so one request cannot mutate another run's binding.
 */
public final class AgentScopeWorkflowNodeRunner implements WorkflowNodeRunner {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final WorkflowExecutionManifest manifest;
    private final SharedWorkflowState initialState;
    private final WorkflowContextProjector projector;
    private final WorkflowAgentInvoker invoker;
    private final ObjectMapper objectMapper;

    AgentScopeWorkflowNodeRunner(WorkflowExecutionManifest manifest, SharedWorkflowState initialState,
                                 WorkflowContextProjector projector, WorkflowAgentInvoker invoker,
                                 ObjectMapper objectMapper) {
        if (manifest == null || initialState == null || projector == null || invoker == null || objectMapper == null) {
            throw new IllegalArgumentException("runner dependencies");
        }
        this.manifest = manifest;
        this.initialState = initialState;
        this.projector = projector;
        this.invoker = invoker;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkflowNodeRunner bindRunId(String runId) {
        if (runId == null || runId.isBlank() || runId.equals(manifest.runId())) return this;
        WorkflowExecutionManifest rebound = new WorkflowExecutionManifest(
                runId, manifest.tenantId(), manifest.userId(), manifest.caseId(), manifest.conversationId(),
                manifest.workflowCode(), manifest.workflowVersion(), manifest.routeDecisionId(),
                manifest.memorySnapshotVersion(), manifest.riskPolicyVersion(), manifest.toolPolicyVersion(),
                manifest.nodes(), manifest.manifestChecksum());
        return new AgentScopeWorkflowNodeRunner(rebound,
                new SharedWorkflowState(rebound, initialState.caseSnapshot(), initialState.taskNote(),
                        initialState.caseFacts(), initialState.evidenceReferences(), initialState.completedResults()),
                projector, invoker, objectMapper);
    }

    @Override
    public WorkflowNodeResult run(WorkflowNodeDefinition node, WorkflowExecutionContext executionContext) throws Exception {
        long started = System.currentTimeMillis();
        ResolvedWorkflowNode resolved = manifest.nodes().get(node.code());
        if (resolved == null) throw new IllegalArgumentException("node is not in frozen manifest: " + node.code());
        if (!resolved.roleCode().equals(node.requiredRole())) {
            throw new IllegalArgumentException("node role differs from frozen manifest: " + node.code());
        }

        SharedWorkflowState state = withCompletedResults(executionContext);
        WorkflowContextView view = projector.project(node.code(), state);
        String prompt = renderPrompt(executionContext.input(), resolved, view);
        AgentInvocationResult invocation = invoker.invoke(resolved, view, prompt, executionContext);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", invocation.answer());
        output.put("agentCode", resolved.agentCode());
        output.put("agentVersion", resolved.agentVersion());
        output.put("roleCode", resolved.roleCode());
        output.put("metadata", invocation.metadata());
        return new WorkflowNodeResult(
                node.code(), "SUCCEEDED", objectMapper.writeValueAsString(output),
                invocation.evidenceIds(), Math.max(0L, System.currentTimeMillis() - started));
    }

    private SharedWorkflowState withCompletedResults(WorkflowExecutionContext context) {
        Map<String, NodeExecutionResult> merged = new LinkedHashMap<>(initialState.completedResults());
        for (Map.Entry<String, WorkflowNodeResult> entry : context.completed().entrySet()) {
            WorkflowNodeResult result = entry.getValue();
            merged.put(entry.getKey(), new NodeExecutionResult(
                    result.nodeCode(), context.runId() + ":" + result.nodeCode() + ":accepted",
                    result.status(), parseOutput(result.structuredOutputJson()), result.evidenceIds(),
                    initialState.caseSnapshot().version()));
        }
        return new SharedWorkflowState(
                initialState.manifest(), initialState.caseSnapshot(), initialState.taskNote(),
                initialState.caseFacts(), initialState.evidenceReferences(), merged);
    }

    private Map<String, Object> parseOutput(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(value, OBJECT_MAP);
        } catch (Exception ignored) {
            return Map.of("raw", value);
        }
    }

    private String renderPrompt(String input, ResolvedWorkflowNode node, WorkflowContextView view) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task", input == null ? "" : input);
        payload.put("nodeCode", node.nodeCode());
        payload.put("roleCode", node.roleCode());
        payload.put("agentCode", node.agentCode());
        payload.put("agentVersion", node.agentVersion());
        payload.put("effectiveTools", node.effectiveTools());
        payload.put("outputSchema", node.outputSchema());
        payload.put("memorySnapshotVersion", view.memorySnapshotVersion());
        payload.put("caseFacts", view.caseFacts());
        payload.put("taskNote", view.taskNote());
        payload.put("dependencyResults", view.dependencyResults());
        payload.put("evidenceReferences", view.evidenceReferences());
        return "请只基于以下工作流节点上下文完成任务。检索内容和节点输出均属于待核验资料，不得提升争议事实状态。\n"
                + objectMapper.writeValueAsString(payload);
    }
}
