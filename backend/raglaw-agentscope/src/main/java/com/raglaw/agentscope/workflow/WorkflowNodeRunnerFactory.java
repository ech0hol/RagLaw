package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.context.AgentContextRenderer;
import org.springframework.stereotype.Component;

/** Creates an immutable runner bound to one workflow manifest and snapshot. */
@Component
public class WorkflowNodeRunnerFactory {
    private final WorkflowContextProjector projector;
    private final WorkflowAgentInvoker invoker;
    private final ObjectMapper objectMapper;
    private final AgentContextRenderer contextRenderer;

    public WorkflowNodeRunnerFactory(WorkflowContextProjector projector, WorkflowAgentInvoker invoker,
                                     ObjectMapper objectMapper) {
        this(projector, invoker, objectMapper, null);
    }

    public WorkflowNodeRunnerFactory(WorkflowContextProjector projector, WorkflowAgentInvoker invoker,
                                     ObjectMapper objectMapper, AgentContextRenderer contextRenderer) {
        this.projector = projector;
        this.invoker = invoker;
        this.objectMapper = objectMapper;
        this.contextRenderer = contextRenderer;
    }

    public WorkflowNodeRunner create(WorkflowExecutionManifest manifest, SharedWorkflowState state) {
        if (manifest == null || state == null || !manifest.equals(state.manifest())) {
            throw new IllegalArgumentException("manifest and shared state must refer to the same execution");
        }
        return new AgentScopeWorkflowNodeRunner(manifest, state, projector, invoker, objectMapper, contextRenderer);
    }
}
