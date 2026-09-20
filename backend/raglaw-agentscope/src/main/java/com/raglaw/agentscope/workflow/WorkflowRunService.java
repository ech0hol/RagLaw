package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.config.RoutingMode;
import com.raglaw.agentscope.config.RoutingProperties;
import com.raglaw.agentscope.domain.*;
import com.raglaw.agentscope.routing.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Policy boundary: high-risk work cannot reach an expert or tool before approval. */
@Service
public class WorkflowRunService {
    public record RouteOutcome(String status, String runId, boolean approvalRequired, WorkflowExecutor.ExecutionResult execution) { }
    private final WorkflowExecutor executor;
    private final WorkflowRunRepository runs;
    private final WorkflowNodeRunRepository nodeRuns;
    private final RoutingProperties properties;
    private static final ObjectMapper MANIFEST_MAPPER = new ObjectMapper();
    private PersistentWorkflowCoordinator coordinator;
    public WorkflowRunService(WorkflowExecutor executor, WorkflowRunRepository runs, WorkflowNodeRunRepository nodeRuns, RoutingProperties properties) {
        this.executor=executor; this.runs=runs; this.nodeRuns=nodeRuns; this.properties=properties;
    }
    @Autowired(required = false)
    public void setCoordinator(PersistentWorkflowCoordinator coordinator) { this.coordinator = coordinator; }
    public RouteOutcome route(RouteDecision decision, String traceId, String input, WorkflowDefinition workflow, WorkflowNodeRunner runner) {
        return route(decision, traceId, traceId, input, workflow, runner);
    }
    public RouteOutcome route(RouteDecision decision, String traceId, String routeDecisionId, String input, WorkflowDefinition workflow, WorkflowNodeRunner runner) {
        if (properties.getMode() != RoutingMode.ENFORCE) return new RouteOutcome("OBSERVED", null, false, null);
        if (decision.riskLevel() == RiskLevel.HIGH || decision.riskLevel() == RiskLevel.CRITICAL || decision.humanApprovalRequired()) {
            WorkflowRunEntity run = create(traceId, routeDecisionId, decision, "WAITING_APPROVAL", "WAITING_APPROVAL"); runs.save(run);
            return new RouteOutcome("approval_required", run.getId(), true, null);
        }
        if (decision.executionMode() != ExecutionMode.MULTI_AGENT_WORKFLOW || workflow == null)
            return new RouteOutcome("SINGLE_AGENT", null, false, null);
        // Never fabricate a successful workflow result when the role-bound runner is not wired.
        // Persist a human-gated state so ENFORCE remains fail-closed during incremental rollout.
        if (runner == null) {
            WorkflowRunEntity run = create(traceId, routeDecisionId, decision, "WAITING_APPROVAL", "WAITING_APPROVAL");
            run.setErrorCode("WORKFLOW_RUNNER_UNAVAILABLE");
            runs.save(run);
            return new RouteOutcome("approval_required", run.getId(), true, null);
        }
        WorkflowRunEntity run=create(traceId, routeDecisionId, decision, "RUNNING", "APPROVED"); runner = bindManifest(run, runner, workflow); runs.save(run);
        if (coordinator == null) {
            run.setStatus("WAITING_APPROVAL"); run.setApprovalStatus("WAITING_APPROVAL"); run.setErrorCode("WORKFLOW_COORDINATOR_UNAVAILABLE"); runs.save(run);
            return new RouteOutcome("approval_required", run.getId(), true, null);
        }
        PersistentWorkflowCoordinator.WorkflowRunOutcome durable = coordinator.executeExisting(run, workflow, traceId, input, runner);
        return new RouteOutcome(durable.status(), run.getId(), false, durable.execution());
    }
    public RouteOutcome approve(String priorRunId, String traceId, RouteDecision decision, WorkflowDefinition workflow, String input, WorkflowNodeRunner runner) {
        if (properties.getMode()!=RoutingMode.ENFORCE) return new RouteOutcome("OBSERVED", null, false, null);
        WorkflowRunEntity prior=runs.findById(priorRunId).orElseThrow();
        if (!"WAITING_APPROVAL".equals(prior.getApprovalStatus()) && !"WAITING_APPROVAL".equals(prior.getStatus())) {
            throw new IllegalStateException("INVALID_APPROVAL_STATE");
        }
        prior.setStatus("APPROVED"); prior.setApprovalStatus("APPROVED"); prior.setCompletedAt(Instant.now()); runs.save(prior);
        RouteDecision approved = new RouteDecision(decision.taskType(), decision.riskLevel(),
                ExecutionMode.MULTI_AGENT_WORKFLOW, decision.expertRole(),
                decision.workflowCode() == null && workflow != null ? workflow.code() : decision.workflowCode(),
                decision.policyReasons(), false, decision.policyVersion());
        return routeApproved(approved, traceId, prior.getRouteDecisionId(), input, workflow, runner);
    }
    public RouteOutcome reject(String runId, String reason) { WorkflowRunEntity run=runs.findById(runId).orElseThrow(); if (!"WAITING_APPROVAL".equals(run.getApprovalStatus()) && !"WAITING_APPROVAL".equals(run.getStatus())) throw new IllegalStateException("INVALID_APPROVAL_STATE"); run.setStatus("REJECTED"); run.setApprovalStatus("REJECTED"); run.setErrorCode(reason); run.setCompletedAt(Instant.now()); runs.save(run); return new RouteOutcome("REJECTED",runId,false,null); }
    private RouteOutcome routeApproved(RouteDecision decision, String traceId, String routeDecisionId, String input,
                                       WorkflowDefinition workflow, WorkflowNodeRunner runner) {
        if (properties.getMode() != RoutingMode.ENFORCE) return new RouteOutcome("OBSERVED", null, false, null);
        if (workflow == null || runner == null) {
            WorkflowRunEntity run = create(traceId, routeDecisionId, decision, "WAITING_APPROVAL", "WAITING_APPROVAL");
            run.setErrorCode("WORKFLOW_RUNNER_UNAVAILABLE"); runs.save(run);
            return new RouteOutcome("approval_required", run.getId(), true, null);
        }
        WorkflowRunEntity run = create(traceId, routeDecisionId, decision, "RUNNING", "APPROVED"); runner = bindManifest(run, runner, workflow); runs.save(run);
        if (coordinator == null) {
            run.setStatus("WAITING_APPROVAL"); run.setApprovalStatus("WAITING_APPROVAL"); run.setErrorCode("WORKFLOW_COORDINATOR_UNAVAILABLE"); runs.save(run);
            return new RouteOutcome("approval_required", run.getId(), true, null);
        }
        PersistentWorkflowCoordinator.WorkflowRunOutcome durable = coordinator.executeExisting(run, workflow, traceId, input, runner);
        return new RouteOutcome(durable.status(), run.getId(), false, durable.execution());
    }
    private static WorkflowRunEntity create(String traceId, String routeDecisionId, RouteDecision d, String status, String approval) { WorkflowRunEntity e=new WorkflowRunEntity(); e.setId(UUID.randomUUID().toString()); e.setTraceId(traceId); e.setRouteDecisionId(routeDecisionId); e.setWorkflowCode(d.workflowCode()==null?"":d.workflowCode()); e.setStatus(status); e.setApprovalStatus(approval); e.setStartedAt(Instant.now()); return e; }
    private static WorkflowNodeRunner bindManifest(WorkflowRunEntity run, WorkflowNodeRunner runner, WorkflowDefinition workflow) {
        if (runner == null) return null;
        WorkflowNodeRunner bound = runner.bindRunId(run.getId());
        if (bound.frozenManifestJson() == null) return bound;
        run.setManifestJson(bound.frozenManifestJson());
        try { run.setWorkflowDefinitionJson(MANIFEST_MAPPER.writeValueAsString(workflow)); }
        catch (Exception exception) { throw new IllegalStateException("workflow definition serialization failed", exception); }
        WorkflowExecutionManifest manifest = bound.frozenManifest();
        if (manifest != null) {
            run.setTenantId(manifest.tenantId());
            run.setUserId(manifest.userId());
            run.setCaseId(manifest.caseId());
            run.setConversationId(manifest.conversationId());
            run.setWorkflowVersion(manifest.workflowVersion());
            run.setMemorySnapshotVersion(manifest.memorySnapshotVersion());
        }
        return bound;
    }
}
