package com.raglaw.agentscope.agui;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentscope.a2a.QuestionRecommender;
import com.raglaw.agentscope.agui.dto.AguiRunRequest;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.expert.ExpertRouter;
import com.raglaw.agentscope.shadow.ShadowRouteObserver;
import com.raglaw.agentscope.routing.TaskRouteObserver;
import com.raglaw.agentscope.routing.TaskRoutingService;
import com.raglaw.agentscope.routing.RoutingRequest;
import com.raglaw.agentscope.routing.WorkflowCatalog;
import com.raglaw.agentscope.config.RoutingMode;
import com.raglaw.agentscope.config.RoutingProperties;
import com.raglaw.agentscope.workflow.WorkflowRunService;
import com.raglaw.agentscope.runtime.AgentRunFactory;
import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.agentscope.trace.TraceContext;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.rag.contract.ContractChatContext;
import com.raglaw.rag.contract.ContractChatContextBuilder;
import com.raglaw.rag.tool.CatalogQueryDetector;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import com.raglaw.rag.tool.RagSearchTool;
import com.raglaw.agentscope.tools.AgentscopeRagSearchTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AguiReactRunFacade {

    private static final Logger log = LoggerFactory.getLogger(AguiReactRunFacade.class);

    private final ExpertRouter expertRouter;
    private final AgentRunFactory agentRunFactory;
    private final AguiEventBridge eventBridge;
    private final TraceRecorder traceRecorder;
    private final ConversationService conversationService;
    private final TaskCancellationRegistry cancellationRegistry;
    private final RagSearchTool ragSearchTool;
    private final ReferencePayloadBuilder referencePayloadBuilder;
    private final QuestionRecommender questionRecommender;
    private final AgentscopeLlmProperties llmProperties;
    private final Environment environment;
    private final ShadowRouteObserver shadowRouteObserver;
    private final ContractChatContextBuilder contractChatContextBuilder;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TaskRouteObserver taskRouteObserver;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private TaskRoutingService taskRoutingService;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private WorkflowRunService workflowRunService;
    @org.springframework.beans.factory.annotation.Autowired(required = false) private RoutingProperties routingProperties;

    public AguiReactRunFacade(
            ExpertRouter expertRouter,
            AgentRunFactory agentRunFactory,
            AguiEventBridge eventBridge,
            TraceRecorder traceRecorder,
            ConversationService conversationService,
            TaskCancellationRegistry cancellationRegistry,
            RagSearchTool ragSearchTool,
            ReferencePayloadBuilder referencePayloadBuilder,
            QuestionRecommender questionRecommender,
            AgentscopeLlmProperties llmProperties,
            Environment environment,
            ShadowRouteObserver shadowRouteObserver,
            ContractChatContextBuilder contractChatContextBuilder
    ) {
        this.expertRouter = expertRouter;
        this.agentRunFactory = agentRunFactory;
        this.eventBridge = eventBridge;
        this.traceRecorder = traceRecorder;
        this.conversationService = conversationService;
        this.cancellationRegistry = cancellationRegistry;
        this.ragSearchTool = ragSearchTool;
        this.referencePayloadBuilder = referencePayloadBuilder;
        this.questionRecommender = questionRecommender;
        this.llmProperties = llmProperties;
        this.environment = environment;
        this.shadowRouteObserver = shadowRouteObserver;
        this.contractChatContextBuilder = contractChatContextBuilder;
    }

    public void executeRun(
            SseEmitter emitter,
            String taskId,
            AguiRunRequest request,
            String userId,
            AgentConfigSnapshot agent,
            String conversationId,
            String userMessage,
            boolean regenerate,
            TraceContext trace,
            String contextDocumentId
    ) throws IOException {
        long startMs = System.currentTimeMillis();

        if (enforceWorkflowBoundary(emitter, trace, userMessage, userId, conversationId, contextDocumentId)) return;

        ExpertContext expert = expertRouter.resolve(agent, userMessage, contextDocumentId);
        observeTaskRouteShadow(trace.traceId(), expert, userMessage);
        shadowRouteObserver.observeAsync(
                trace.traceId(),
                userMessage,
                expert.knowledgeScopes(),
                contextDocumentId,
                expert.lowConfidence(),
                expert.routeReason(),
                agent.a2aPeers()
        );
        AgentRunSession session = new AgentRunSession(taskId);
        session.setTraceId(trace.traceId());
        session.setUserMessage(userMessage);

        checkCancelled(taskId, session);
        eventBridge.sendDelegatedStatus(emitter, expert);

        if (expert.delegated()) {
            traceRecorder.recordA2aCall(
                    trace.traceId(),
                    agent.code(),
                    expert.peerCode(),
                    userMessage,
                    "委派至专家 " + expert.peerName(),
                    0L
            );
            traceRecorder.recordStage(
                    trace.traceId(),
                    "a2a_delegate",
                    Map.of(
                            "peer", expert.peerCode(),
                            "routeReason", expert.routeReason(),
                            "usedLlm", expert.usedLlm(),
                            "lowConfidence", expert.lowConfidence()
                    ),
                    0L
            );
        }

        AguiSseWriter.send(emitter, "status", Map.of("message", "正在处理您的问题…"));

        maybePresearchCatalog(emitter, session, expert, userMessage, trace);
        maybePresearchKnowledge(emitter, session, expert, userMessage, trace);

        ContractChatContext chatContext = contractChatContextBuilder.enrichUserMessage(
                contextDocumentId,
                userMessage
        );
        String llmMessage = chatContext.message();
        if (chatContext.injected()) {
            traceRecorder.recordStage(
                    trace.traceId(),
                    "contract_chat_context",
                    Map.of(
                            "documentId", contextDocumentId,
                            "textChars", chatContext.textChars(),
                            "riskCount", chatContext.riskCount()
                    ),
                    0L
            );
        }

        String fullText;
        long llmStart = System.currentTimeMillis();
        if (useMockLlm()) {
            fullText = executeMockRun(emitter, taskId, session, expert, llmMessage, trace);
        } else {
            String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("DASHSCOPE_API_KEY not set, falling back to mock LLM");
                fullText = executeMockRun(emitter, taskId, session, expert, llmMessage, trace);
            } else {
                fullText = executeReactRun(emitter, taskId, session, expert, llmMessage, agent.model(), apiKey);
            }
        }

        boolean hasRetrievalEvidence = !referencePayloadBuilder.relevantCitableHits(session.hits(), userMessage).isEmpty()
                || !session.webReferences().isEmpty();
        fullText = RagAnswerContextBuilder.postProcess(fullText, hasRetrievalEvidence);

        traceRecorder.recordStage(
                trace.traceId(),
                "llm",
                Map.of(
                        "model", agent.model(),
                        "regenerate", regenerate,
                        "mock", useMockLlm(),
                        "react", true
                ),
                System.currentTimeMillis() - llmStart
        );

        traceRecorder.recordLlmUsage(
                trace.traceId(),
                agent.model(),
                estimateTokens(llmMessage),
                estimateTokens(fullText),
                fullText,
                System.currentTimeMillis() - llmStart
        );

        List<RagSearchHit> hits = session.hits();
        traceRecorder.recordChunks(trace.traceId(), hits);

        recordSearchStages(trace, expert, userMessage, session, session.lastRagSearchLatencyMs());

        long latency = System.currentTimeMillis() - startMs;
        traceRecorder.complete(trace.traceId(), latency);

        emitReferences(emitter, session, userMessage);

        conversationService.appendMessage(
                        userId,
                        conversationId,
                        "assistant",
                        fullText,
                        referencePayloadBuilder.serializeJson(hits, session.webReferences(), userMessage))
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));

        List<String> recommendations = questionRecommender.recommend(userMessage, fullText, agent.code(), 3);
        AguiSseWriter.send(emitter, "recommend", Map.of("questions", recommendations));

        AguiSseWriter.send(emitter, "done", Map.of(
                "messageId", trace.messageId(),
                "traceId", trace.traceId(),
                "content", fullText,
                "latencyMs", latency
        ));
        emitter.complete();
    }

    private boolean enforceWorkflowBoundary(SseEmitter emitter, TraceContext trace, String query, String userId,
                                            String conversationId, String contextDocumentId) throws IOException {
        if (routingProperties == null || routingProperties.getMode() != RoutingMode.ENFORCE || taskRoutingService == null || workflowRunService == null) return false;
        var decision = taskRoutingService.route(new RoutingRequest("default", userId, contextDocumentId == null ? "none" : contextDocumentId,
                conversationId, query, contextDocumentId != null, false));
        var classification = new com.raglaw.agentscope.routing.TaskClassification(decision.taskType(), java.util.Set.of(), 1.0, java.util.List.of(), query, "facade", "facade");
        var workflow = WorkflowCatalog.standard().match(classification).orElse(null);
        // The production role-bound runner is injected in the workflow execution layer.
        // Keep this boundary fail-closed while that adapter is unavailable; never emit a
        // fabricated successful answer from the request facade.
        var outcome = workflowRunService.route(decision, trace.traceId(), trace.traceId(), query, workflow, null);
        if (outcome.approvalRequired()) {
            AguiSseWriter.send(emitter, "approval_required", java.util.Map.of("traceId", trace.traceId(), "runId", outcome.runId())); emitter.complete(); return true;
        }
        if (outcome.execution() != null) {
            AguiSseWriter.send(emitter, "done", java.util.Map.of("traceId", trace.traceId(), "status", outcome.status(), "runId", outcome.runId())); emitter.complete(); return true;
        }
        return false;
    }

    /** Additive seam for shadow observation; it never changes the expert selected above. */
    public void observeTaskRouteShadow(String traceId, ExpertContext expert, String userMessage) {
        if (taskRouteObserver != null) taskRouteObserver.observeAsync(traceId, expert, userMessage);
    }

    private String executeMockRun(
            SseEmitter emitter,
            String taskId,
            AgentRunSession session,
            ExpertContext expert,
            String userMessage,
            TraceContext trace
    ) throws IOException {
        long ragStart = System.currentTimeMillis();
        RagSearchResult searchResult = ragSearchTool.searchDetailed(
                userMessage,
                expert.knowledgeScopes(),
                5,
                expert.searchAgentCode(),
                expert.contextDocumentId(),
                expert.lowConfidence(),
                expert.routeReason()
        );
        session.recordSearch(searchResult, System.currentTimeMillis() - ragStart);

        String response = buildMockResponse(expert.peerName(), userMessage, session.hits());
        for (int i = 0; i < response.length(); i++) {
            checkCancelled(taskId, session);
            String delta = response.substring(i, i + 1);
            AguiSseWriter.send(emitter, "text", Map.of("delta", delta));
        }
        return response;
    }

    private String executeReactRun(
            SseEmitter emitter,
            String taskId,
            AgentRunSession session,
            ExpertContext expert,
            String userMessage,
            String model,
            String apiKey
    ) throws IOException {
        AguiEventBridge.AguiStreamState streamState = eventBridge.newStreamState();
        try (ReActAgent agent = agentRunFactory.build(expert, model, apiKey)) {
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .sessionId(taskId)
                    .put(ExpertContext.class, expert)
                    .put(AgentRunSession.class, session)
                    .build();

            agent.streamEvents(new UserMessage(userMessage), runtimeContext)
                    .doOnNext(event -> {
                        try {
                            checkCancelled(taskId, session);
                            if (session.isCancelled()) {
                                agent.interrupt(runtimeContext);
                                return;
                            }
                            eventBridge.onEvent(emitter, event, streamState, session);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .blockLast();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }

        String text = streamState.text();
        if (text.isBlank()) {
            throw new IOException("模型未返回内容");
        }
        return text;
    }

    private void emitReferences(SseEmitter emitter, AgentRunSession session, String userQuery) throws IOException {
        eventBridge.emitKnowledgeReferences(emitter, session, userQuery);
    }

    private void maybePresearchCatalog(
            SseEmitter emitter,
            AgentRunSession session,
            ExpertContext expert,
            String userMessage,
            TraceContext trace
    ) throws IOException {
        boolean catalogQuery = CatalogQueryDetector.isCatalogQuery(userMessage);
        List<String> tools = expert.tools() == null ? List.of() : expert.tools();
        boolean hasRagSearch = tools.contains(AgentscopeRagSearchTool.TOOL_NAME);
        if (!catalogQuery) {
            log.debug("catalog presearch skipped: not a catalog query");
            return;
        }
        if (!hasRagSearch) {
            log.info(
                    "catalog presearch skipped: expert {} has no rag_search tool (tools={})",
                    expert.peerCode(),
                    tools
            );
            return;
        }
        log.info("catalog presearch starting for expert {} query={}", expert.peerCode(), userMessage);
        AguiSseWriter.send(emitter, "status", Map.of("message", "正在检索知识库…"));
        long ragStart = System.currentTimeMillis();
        RagSearchResult searchResult = ragSearchTool.searchDetailed(
                userMessage,
                expert.knowledgeScopes(),
                30,
                expert.searchAgentCode(),
                expert.contextDocumentId(),
                expert.lowConfidence(),
                expert.routeReason()
        );
        session.recordSearch(searchResult, System.currentTimeMillis() - ragStart);
        int hitCount = session.hits().size();
        log.info(
                "catalog presearch finished: hitCount={} catalogMode={}",
                hitCount,
                searchResult.retrievalTrace() != null
                        ? searchResult.retrievalTrace().get("catalogMode")
                        : null
        );
        if (trace.traceId() != null && searchResult.retrievalTrace() != null) {
            Map<String, Object> stage = new java.util.HashMap<>(searchResult.retrievalTrace());
            stage.put("hitCount", hitCount);
            stage.put("presearch", true);
            stage.put("originalQuery", userMessage);
            traceRecorder.recordStage(trace.traceId(), "catalog_presearch", stage, System.currentTimeMillis() - ragStart);
        }
        eventBridge.emitKnowledgeReferences(emitter, session, userMessage);
        AguiSseWriter.send(emitter, "status", Map.of("message", "正在生成回答…"));
    }

    private void maybePresearchKnowledge(
            SseEmitter emitter,
            AgentRunSession session,
            ExpertContext expert,
            String userMessage,
            TraceContext trace
    ) throws IOException {
        if (CatalogQueryDetector.isCatalogQuery(userMessage)) {
            return;
        }
        if (useMockLlm()) {
            return;
        }
        List<String> tools = expert.tools() == null ? List.of() : expert.tools();
        if (!tools.contains(AgentscopeRagSearchTool.TOOL_NAME)) {
            return;
        }
        if (session.presearchCompleted()) {
            return;
        }
        log.info("knowledge presearch starting for expert {} query={}", expert.peerCode(), userMessage);
        AguiSseWriter.send(emitter, "status", Map.of("message", "正在检索知识库…"));
        long ragStart = System.currentTimeMillis();
        RagSearchResult searchResult = ragSearchTool.searchDetailed(
                userMessage,
                expert.knowledgeScopes(),
                5,
                expert.searchAgentCode(),
                expert.contextDocumentId(),
                expert.lowConfidence(),
                expert.routeReason()
        );
        session.recordSearch(searchResult, System.currentTimeMillis() - ragStart);
        session.markPresearchCompleted();
        if (trace.traceId() != null && searchResult.retrievalTrace() != null) {
            Map<String, Object> stage = new java.util.HashMap<>(searchResult.retrievalTrace());
            stage.put("hitCount", session.hits().size());
            stage.put("presearch", true);
            stage.put("originalQuery", userMessage);
            traceRecorder.recordStage(trace.traceId(), "knowledge_presearch", stage, System.currentTimeMillis() - ragStart);
        }
        log.info("knowledge presearch finished: hitCount={}", session.hits().size());
    }

    private static String buildMockResponse(String displayName, String question, List<RagSearchHit> hits) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(displayName).append(" 回复\n\n");
        sb.append("针对您的问题「").append(question).append("」，");
        if (!hits.isEmpty()) {
            sb.append("结合相关法规分析如下：\n\n");
            sb.append("1. 可依据检索到的相关条文，需结合具体事实判断权利义务。\n\n");
            sb.append("2. 建议先固定证据、明确争议焦点，再选择协商、调解或诉讼等救济途径。");
        } else {
            sb.append("建议结合具体事实与适用法规进一步分析权利义务。");
        }
        sb.append("\n\n本回复仅供参考，不构成法律意见。");
        return sb.toString();
    }

    private void checkCancelled(String taskId, AgentRunSession session) {
        if (cancellationRegistry.isCancelled(taskId)) {
            session.cancel();
            throw new AguiRunService.TaskCancelledException(taskId);
        }
    }

    private boolean useMockLlm() {
        return llmProperties.isMock() || environment.matchesProfiles("test");
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    private void recordSearchStages(
            TraceContext trace,
            ExpertContext expert,
            String userMessage,
            AgentRunSession session,
            long ragLatencyMs
    ) {
        var funnel = session.lastFunnelResult();
        if (funnel != null) {
            traceRecorder.recordStage(
                    trace.traceId(),
                    "knowledge_funnel",
                    Map.of(
                            "scopePaths", funnel.scopePaths(),
                            "topicPaths", funnel.topicPaths(),
                            "lockedDocumentIds", funnel.lockedDocumentIds(),
                            "topicConfidence", funnel.topicConfidence(),
                            "documentConfidence", funnel.documentConfidence(),
                            "degradationLevel", funnel.degradationLevel(),
                            "scopeRouteReason", funnel.scopeRouteReason() == null ? "" : funnel.scopeRouteReason(),
                            "lowConfidence", funnel.lowConfidence()
                    ),
                    0L
            );
        }
        Map<String, Object> retrievalTrace = new java.util.HashMap<>(session.lastRetrievalTrace());
        retrievalTrace.put("hitCount", session.hits().size());
        retrievalTrace.put("scopes", expert.knowledgeScopes());
        retrievalTrace.put("contextDocumentId", expert.contextDocumentId() == null ? "" : expert.contextDocumentId());
        retrievalTrace.put("originalQuery", userMessage);
        retrievalTrace.put("react", true);
        if (funnel != null) {
            retrievalTrace.put("lockedDocs", funnel.lockedDocumentIds());
            retrievalTrace.put("degradation", funnel.degradationLevel());
            try {
                shadowRouteObserver.observeDocumentFromFunnel(trace.traceId(), expert.contextDocumentId(), funnel);
            } catch (Exception e) {
                log.warn("Shadow document observation failed for trace {}", trace.traceId(), e);
            }
        }
        traceRecorder.recordStage(trace.traceId(), "dual_channel_retrieval", retrievalTrace, 0L);
        traceRecorder.recordStage(trace.traceId(), "rag_search", retrievalTrace, ragLatencyMs);
    }
}
