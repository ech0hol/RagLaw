package com.raglaw.agentscope.agui;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.agentscope.a2a.A2aOrchestrator;
import com.raglaw.agentscope.a2a.QuestionRecommender;
import com.raglaw.agentscope.agui.dto.AguiRunRequest;
import com.raglaw.agentscope.config.AgentscopeLlmProperties;
import com.raglaw.agentscope.trace.TraceContext;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchTool;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AguiRunService {

    private static final Logger log = LoggerFactory.getLogger(AguiRunService.class);
    private static final String DEFAULT_AGENT = "GENERAL";

    private final AgentRegistry agentRegistry;
    private final RagSearchTool ragSearchTool;
    private final TraceRecorder traceRecorder;
    private final ConversationService conversationService;
    private final TaskCancellationRegistry cancellationRegistry;
    private final DashScopeClient dashScopeClient;
    private final AgentscopeLlmProperties llmProperties;
    private final Environment environment;
    private final A2aOrchestrator a2aOrchestrator;
    private final QuestionRecommender questionRecommender;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AguiRunService(
            AgentRegistry agentRegistry,
            RagSearchTool ragSearchTool,
            TraceRecorder traceRecorder,
            ConversationService conversationService,
            TaskCancellationRegistry cancellationRegistry,
            DashScopeClient dashScopeClient,
            AgentscopeLlmProperties llmProperties,
            Environment environment,
            A2aOrchestrator a2aOrchestrator,
            QuestionRecommender questionRecommender
    ) {
        this.agentRegistry = agentRegistry;
        this.ragSearchTool = ragSearchTool;
        this.traceRecorder = traceRecorder;
        this.conversationService = conversationService;
        this.cancellationRegistry = cancellationRegistry;
        this.dashScopeClient = dashScopeClient;
        this.llmProperties = llmProperties;
        this.environment = environment;
        this.a2aOrchestrator = a2aOrchestrator;
        this.questionRecommender = questionRecommender;
    }

    public SseEmitter run(AguiRunRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String taskId = UUID.randomUUID().toString();
        cancellationRegistry.register(taskId);

        String userId = CurrentUserHolder.get();
        if (environment.matchesProfiles("test")) {
            try {
                executeRun(emitter, taskId, request, userId);
            } catch (Exception e) {
                log.error("AG-UI run failed", e);
                emitter.completeWithError(e);
            } finally {
                cancellationRegistry.unregister(taskId);
            }
            return emitter;
        }
        executor.execute(() -> {
            try {
                executeRun(emitter, taskId, request, userId);
            } catch (Exception e) {
                log.error("AG-UI run failed", e);
                emitter.completeWithError(e);
            } finally {
                cancellationRegistry.unregister(taskId);
            }
        });
        return emitter;
    }

    public boolean stop(String taskId) {
        cancellationRegistry.cancel(taskId);
        return true;
    }

    private void executeRun(
            SseEmitter emitter,
            String taskId,
            AguiRunRequest request,
            String userId
    ) throws IOException {
        long startMs = System.currentTimeMillis();
        String agentCode = resolveAgentCode(request, userId);
        AgentConfigSnapshot agent = resolveAgent(agentCode);
        String conversationId = resolveConversationId(request, userId, agentCode);
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        String userMessage;
        if (regenerate) {
            userMessage = conversationService.findLastUserMessageContent(userId, conversationId)
                    .orElseThrow(() -> new IllegalArgumentException("找不到可重新生成的用户消息"));
        } else {
            userMessage = request.message();
            if (userMessage == null || userMessage.isBlank()) {
                throw new IllegalArgumentException("消息不能为空");
            }
            conversationService.appendMessage(userId, conversationId, "user", userMessage, null)
                    .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));
        }

        TraceContext trace = traceRecorder.start(
                conversationId,
                userId,
                userMessage,
                agent.code()
        );

        AguiSseWriter.send(emitter, "meta", Map.of(
                "taskId", taskId,
                "traceId", trace.traceId(),
                "messageId", trace.messageId(),
                "conversationId", conversationId,
                "agentCode", agent.code()
        ));

        checkCancelled(taskId);
        AguiSseWriter.send(emitter, "status", Map.of("message", "正在处理您的问题…"));

        List<RagSearchHit> hits = List.of();
        String delegatedPeer = null;
        if ("GENERAL".equals(agent.code())
                && agent.a2aPeers() != null
                && !agent.a2aPeers().isEmpty()) {
            A2aOrchestrator.A2aResult a2aResult = a2aOrchestrator.delegate(
                    agent,
                    userMessage,
                    trace.traceId(),
                    emitter
            );
            if (a2aResult.hasPeer()) {
                hits = a2aResult.hits();
                delegatedPeer = a2aResult.peerName();
            }
        } else if (agent.tools().contains("rag_search")) {
            long ragStart = System.currentTimeMillis();
            hits = ragSearchTool.search(userMessage, agent.knowledgeScopes(), 5, agent.code());
            traceRecorder.recordStage(
                    trace.traceId(),
                    "rag_search",
                    Map.of("hitCount", hits.size(), "scopes", agent.knowledgeScopes()),
                    System.currentTimeMillis() - ragStart
            );
        }

        emitReferences(emitter, hits);
        traceRecorder.recordChunks(trace.traceId(), hits);

        checkCancelled(taskId);

        String ragContext = buildRagContext(hits, delegatedPeer);
        String userMessageWithContext = ragContext.isBlank()
                ? userMessage
                : ragContext + "\n\n用户问题：" + userMessage;

        String fullText;
        Integer promptTokens = null;
        Integer completionTokens = null;
        long llmStart = System.currentTimeMillis();

        if (useMockLlm()) {
            fullText = streamMockResponse(emitter, taskId, userMessageWithContext, agent, hits);
            promptTokens = estimateTokens(userMessageWithContext);
            completionTokens = estimateTokens(fullText);
        } else {
            String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("DASHSCOPE_API_KEY not set, falling back to mock LLM");
                fullText = streamMockResponse(emitter, taskId, userMessageWithContext, agent, hits);
                promptTokens = estimateTokens(userMessageWithContext);
                completionTokens = estimateTokens(fullText);
            } else {
                DashScopeClient.LlmStreamResult result = streamDashScope(
                        emitter,
                        taskId,
                        apiKey,
                        agent,
                        userMessageWithContext
                );
                fullText = result.text();
                promptTokens = result.promptTokens();
                completionTokens = result.completionTokens();
            }
        }

        traceRecorder.recordStage(
                trace.traceId(),
                "llm",
                Map.of(
                        "model", agent.model(),
                        "regenerate", regenerate,
                        "mock", useMockLlm()
                ),
                System.currentTimeMillis() - llmStart
        );

        traceRecorder.recordLlmUsage(
                trace.traceId(),
                agent.model(),
                promptTokens,
                completionTokens
        );

        long latency = System.currentTimeMillis() - startMs;
        traceRecorder.complete(trace.traceId(), latency);

        conversationService.appendMessage(userId, conversationId, "assistant", fullText, null)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));

        List<String> recommendations = questionRecommender.recommend(userMessage, agent.code(), 3);
        AguiSseWriter.send(emitter, "recommend", Map.of("questions", recommendations));

        AguiSseWriter.send(emitter, "done", Map.of(
                "messageId", trace.messageId(),
                "traceId", trace.traceId(),
                "content", fullText,
                "latencyMs", latency
        ));
        emitter.complete();
    }

    private DashScopeClient.LlmStreamResult streamDashScope(
            SseEmitter emitter,
            String taskId,
            String apiKey,
            AgentConfigSnapshot agent,
            String userMessage
    ) throws IOException {
        try {
            return dashScopeClient.streamChat(
                    apiKey,
                    agent.model(),
                    agent.systemPrompt(),
                    userMessage,
                    delta -> {
                        try {
                            checkCancelled(taskId);
                            AguiSseWriter.send(emitter, "text", Map.of("delta", delta));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    () -> checkCancelled(taskId)
            );
        } catch (Exception e) {
            if (e instanceof RuntimeException re && re.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("DashScope stream failed", e);
        }
    }

    private String streamMockResponse(
            SseEmitter emitter,
            String taskId,
            String userMessage,
            AgentConfigSnapshot agent,
            List<RagSearchHit> hits
    ) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(agent.name()).append("】");
        if (!hits.isEmpty()) {
            sb.append("根据知识库检索到 ").append(hits.size()).append(" 条相关依据。");
            sb.append("例如：").append(hits.get(0).excerpt());
            if (hits.size() > 1) {
                sb.append(" 等。");
            }
            sb.append("\n\n");
        }
        sb.append("针对您的问题「").append(extractUserQuestion(userMessage)).append("」，");
        sb.append("建议结合上述法规条文分析具体事实。本回复仅供参考，不构成法律意见。");
        String response = sb.toString();
        for (int i = 0; i < response.length(); i++) {
            checkCancelled(taskId);
            String delta = response.substring(i, i + 1);
            AguiSseWriter.send(emitter, "text", Map.of("delta", delta));
        }
        return response;
    }

    private static void emitReferences(SseEmitter emitter, List<RagSearchHit> hits) throws IOException {
        for (int i = 0; i < hits.size(); i++) {
            RagSearchHit hit = hits.get(i);
            AguiSseWriter.send(emitter, "reference", Map.of(
                    "index", i + 1,
                    "chunkId", hit.chunkId(),
                    "documentId", hit.documentId() != null ? hit.documentId() : "",
                    "path", hit.l1L2L3Path(),
                    "excerpt", hit.excerpt(),
                    "score", hit.score()
            ));
        }
    }

    private static String buildRagContext(List<RagSearchHit> hits, String delegatedPeer) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (delegatedPeer != null && !delegatedPeer.isBlank()) {
            sb.append("已通过专家助手「").append(delegatedPeer).append("」检索到参考依据：\n");
        } else {
            sb.append("以下是从知识库检索到的参考条文（请优先依据这些内容回答，并标注引用序号）：\n");
        }
        for (int i = 0; i < hits.size(); i++) {
            RagSearchHit hit = hits.get(i);
            sb.append('[').append(i + 1).append("] ").append(hit.excerpt()).append('\n');
        }
        return sb.toString();
    }

    private static String extractUserQuestion(String userMessageWithContext) {
        int marker = userMessageWithContext.lastIndexOf("用户问题：");
        if (marker >= 0) {
            return userMessageWithContext.substring(marker + "用户问题：".length()).trim();
        }
        return userMessageWithContext;
    }

    private boolean useMockLlm() {
        return llmProperties.isMock() || environment.matchesProfiles("test");
    }

    private String resolveConversationId(AguiRunRequest request, String userId, String agentCode) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("未登录，无法创建会话");
        }
        if (Boolean.TRUE.equals(request.regenerate())) {
            if (request.conversationId() == null || request.conversationId().isBlank()) {
                throw new IllegalArgumentException("重新生成需要已有会话");
            }
            if (conversationService.get(userId, request.conversationId()).isEmpty()) {
                throw new IllegalArgumentException("会话不存在或无权访问");
            }
            return request.conversationId();
        }
        if (request.conversationId() != null && !request.conversationId().isBlank()
                && conversationService.get(userId, request.conversationId()).isPresent()) {
            return request.conversationId();
        }
        return conversationService.create(userId, agentCode).id();
    }

    private String resolveAgentCode(AguiRunRequest request, String userId) {
        if (request.agentCode() != null && !request.agentCode().isBlank()) {
            return request.agentCode();
        }
        if (userId != null
                && request.conversationId() != null
                && !request.conversationId().isBlank()) {
            return conversationService.get(userId, request.conversationId())
                    .map(conversation -> conversation.agentCode())
                    .filter(code -> code != null && !code.isBlank())
                    .orElse(DEFAULT_AGENT);
        }
        return DEFAULT_AGENT;
    }

    private AgentConfigSnapshot resolveAgent(String agentCode) {
        AgentConfigSnapshot agent = agentRegistry.get(agentCode);
        if (agent != null) {
            return agent;
        }
        if (!DEFAULT_AGENT.equals(agentCode)) {
            AgentConfigSnapshot fallback = agentRegistry.get(DEFAULT_AGENT);
            if (fallback != null) {
                return fallback;
            }
        }
        throw new IllegalArgumentException("No enabled agent available for: " + agentCode);
    }

    private void checkCancelled(String taskId) {
        if (cancellationRegistry.isCancelled(taskId)) {
            throw new TaskCancelledException(taskId);
        }
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    static class TaskCancelledException extends RuntimeException {
        TaskCancelledException(String taskId) {
            super("Task cancelled: " + taskId);
        }
    }
}
