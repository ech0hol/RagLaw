package com.raglaw.agentscope.agui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.agentadmin.registry.AgentVersionRegistry;
import com.raglaw.agentscope.a2a.QuestionRecommender;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.config.RoutingMode;
import com.raglaw.agentscope.config.RoutingProperties;
import com.raglaw.agentscope.expert.ExpertRouter;
import com.raglaw.agentscope.routing.ExecutionMode;
import com.raglaw.agentscope.routing.RiskLevel;
import com.raglaw.agentscope.routing.RouteDecision;
import com.raglaw.agentscope.routing.TaskRoutingService;
import com.raglaw.agentscope.routing.TaskType;
import com.raglaw.agentscope.runtime.AgentRunFactory;
import com.raglaw.agentscope.shadow.ShadowRouteObserver;
import com.raglaw.agentscope.tools.AgentscopeRagSearchTool;
import com.raglaw.agentscope.trace.TraceContext;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.agentscope.workflow.RoleResolver;
import com.raglaw.agentscope.workflow.WorkflowAgentInvoker;
import com.raglaw.agentscope.workflow.WorkflowManifestFactory;
import com.raglaw.agentscope.workflow.WorkflowNodeRunner;
import com.raglaw.agentscope.workflow.WorkflowNodeRunnerFactory;
import com.raglaw.agentscope.workflow.WorkflowNodeResult;
import com.raglaw.agentscope.workflow.WorkflowExecutor;
import com.raglaw.agentscope.workflow.WorkflowRunService;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.CaseMemorySnapshot;
import com.raglaw.memory.service.MemorySnapshotService;
import com.raglaw.rag.contract.ContractChatContextBuilder;
import com.raglaw.rag.tool.RagSearchTool;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AguiWorkflowRoutingTest {

    @Test
    void enforceRoutePassesManifestBoundRunnerToWorkflowService() throws Exception {
        TaskRoutingService routing = mock(TaskRoutingService.class);
        WorkflowRunService workflowRunService = mock(WorkflowRunService.class);
        RoutingProperties routingProperties = new RoutingProperties();
        routingProperties.setMode(RoutingMode.ENFORCE);
        ConversationService conversations = mock(ConversationService.class);
        MemorySnapshotService snapshots = mock(MemorySnapshotService.class);
        WorkflowNodeRunnerFactory runnerFactory = mock(WorkflowNodeRunnerFactory.class);
        WorkflowNodeRunner runner = mock(WorkflowNodeRunner.class);
        AgentVersionRegistry registry = new AgentVersionRegistry();
        registry.reload(List.of(snapshot()));

        RouteDecision decision = new RouteDecision(
                TaskType.DISPUTE_ANALYSIS, RiskLevel.LOW, ExecutionMode.MULTI_AGENT_WORKFLOW,
                "LEGAL_SYNTHESIZER", "LABOR_DISPUTE_REVIEW", List.of("complex"), false, "risk-v1");
        when(routing.route(any())).thenReturn(decision);
        when(conversations.findCaseId("user-1", "conversation-1")).thenReturn(Optional.of("case-1"));
        when(snapshots.freeze(new CaseScope("default", "user-1", "case-1")))
                .thenReturn(new CaseMemorySnapshot(new CaseScope("default", "user-1", "case-1"), 18, Instant.parse("2025-01-01T00:00:00Z")));
        when(runnerFactory.create(any(), any())).thenReturn(runner);
        when(workflowRunService.route(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new WorkflowRunService.RouteOutcome("SINGLE_AGENT", null, false, null));

        AguiReactRunFacade facade = newFacade(routing, workflowRunService, routingProperties, conversations);
        set(facade, "memorySnapshotService", snapshots);
        set(facade, "workflowNodeRunnerFactory", runnerFactory);
        set(facade, "workflowManifestFactory", new WorkflowManifestFactory(new ObjectMapper()));
        set(facade, "roleResolver", new RoleResolver());
        set(facade, "agentVersionRegistry", registry);

        var method = AguiReactRunFacade.class
                .getDeclaredMethod("enforceWorkflowBoundary", SseEmitter.class, TraceContext.class, String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean handled = (boolean) method.invoke(
                facade, new SseEmitter(), TraceContext.create(), "劳动争议分析", "user-1", "conversation-1", null);

        assertThat(handled).isFalse();
        verify(workflowRunService).route(any(), anyString(), anyString(), anyString(), any(), org.mockito.ArgumentMatchers.same(runner));
    }

    @Test
    void workflowExecutionExposesSynthesisAnswerAsResponseContent() throws Exception {
        AguiReactRunFacade facade = newFacade(
                mock(TaskRoutingService.class), mock(WorkflowRunService.class), new RoutingProperties(), mock(ConversationService.class));
        WorkflowExecutor.ExecutionResult execution = new WorkflowExecutor.ExecutionResult(
                WorkflowExecutor.Status.SUCCEEDED,
                Map.of("SYNTHESIS", new WorkflowNodeResult(
                        "SYNTHESIS", "SUCCEEDED", "{\"answer\":\"综合结论\",\"agentVersion\":2}", List.of(), 12L)),
                null);

        var method = AguiReactRunFacade.class.getDeclaredMethod("extractWorkflowAnswer", WorkflowExecutor.ExecutionResult.class);
        method.setAccessible(true);
        assertThat(method.invoke(facade, execution)).isEqualTo("综合结论");
    }

    private AguiReactRunFacade newFacade(TaskRoutingService routing, WorkflowRunService workflowRunService,
                                         RoutingProperties routingProperties, ConversationService conversations) {
        AguiReactRunFacade facade = new AguiReactRunFacade(
                mock(ExpertRouter.class), mock(AgentRunFactory.class), mock(AguiEventBridge.class), mock(TraceRecorder.class),
                conversations, new TaskCancellationRegistry(), mock(RagSearchTool.class), mock(ReferencePayloadBuilder.class),
                mock(QuestionRecommender.class), new AgentscopeLlmProperties(), mock(Environment.class),
                mock(ShadowRouteObserver.class), mock(ContractChatContextBuilder.class));
        set(facade, "taskRoutingService", routing);
        set(facade, "workflowRunService", workflowRunService);
        set(facade, "routingProperties", routingProperties);
        return facade;
    }

    private static void set(Object target, String field, Object value) {
        try {
            var declared = target.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private AgentVersionSnapshot snapshot() {
        return new AgentVersionSnapshot(
                "legal-expert", 1, AgentPublishStatus.PUBLISHED, "dashscope:qwen-plus", "prompt",
                new AgentCapabilityManifest(Set.of("LEGAL"), Set.of("ANALYSIS"), Set.of("DISPUTE_ANALYSIS"),
                        Set.of("LOW"), Set.of(), "answer"), new AgentToolPolicy(List.of(), Set.of()),
                List.of(), List.of("LABOR"), List.of(), 0.95, "checksum");
    }
}
