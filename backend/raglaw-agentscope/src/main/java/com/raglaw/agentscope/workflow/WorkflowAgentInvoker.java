package com.raglaw.agentscope.workflow;

/**
 * Runtime boundary for invoking one already-resolved expert.
 * The invoker may call AgentScope, but it cannot select a role or widen tools.
 */
@FunctionalInterface
public interface WorkflowAgentInvoker {
    AgentInvocationResult invoke(ResolvedWorkflowNode node, WorkflowContextView context,
                                 String prompt, WorkflowExecutionContext executionContext) throws Exception;
}
