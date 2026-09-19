package com.raglaw.agentscope.workflow;

import java.util.Map;

public record WorkflowExecutionContext(String runId, String traceId, String input, Map<String, WorkflowNodeResult> completed,
                                       WorkflowExecutor.CancellationToken cancellation) {
    public WorkflowExecutionContext(String runId, String traceId, String input, Map<String, WorkflowNodeResult> completed) {
        this(runId, traceId, input, completed, new WorkflowExecutor.CancellationToken());
    }
    public WorkflowExecutionContext {
        completed = completed == null ? Map.of() : Map.copyOf(completed);
        cancellation = cancellation == null ? new WorkflowExecutor.CancellationToken() : cancellation;
    }
    public boolean cancelled() { return cancellation.cancelled(); }
}
