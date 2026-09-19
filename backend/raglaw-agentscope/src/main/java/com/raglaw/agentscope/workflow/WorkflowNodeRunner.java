package com.raglaw.agentscope.workflow;

/** Role-bound seam for invoking an expert. The executor never chooses tools or roles. */
@FunctionalInterface
public interface WorkflowNodeRunner {
    WorkflowNodeResult run(WorkflowNodeDefinition node, WorkflowExecutionContext context) throws Exception;
}
