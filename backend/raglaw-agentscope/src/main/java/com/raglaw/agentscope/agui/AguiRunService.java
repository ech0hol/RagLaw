package com.raglaw.agentscope.agui;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AguiRunService(
            AgentRegistry agentRegistry,
            RagSearchTool ragSearchTool,
            TraceRecorder traceRecorder,
            ConversationService conversationService,
            TaskCancellationRegistry cancellationRegistry,
            DashScopeClient dashScopeClient,
            AgentscopeLlmProperties llmProperties,
            Environment environment
    ) {
        this.agentRegistry = agentRegistry;
        this.ragSearchTool = ragSearchTool;
        this.traceRecorder = traceRecorder;
        this.conversationService = conversationService;
        this.cancellationRegistry = cancellationRegistry;
        this.dashScopeClient = dashScopeClient;
        this.llmProperties = llmProperties;
        this.environment = environment;
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
        String agentCode = resolveAgentCode(request.agentCode());
        AgentConfigSnapshot agent = resolveAgent(agentCode);
        String conversationId = resolveConversationId(request, userId, agentCode);

        conversationService.appendMessage(userId, conversationId, "user", request.message(), null)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));

        TraceContext trace = traceRecorder.start(
                conversationId,
                userId,
                request.message(),
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
        if (agent.tools().contains("rag_search")) {
            long ragStart = System.currentTimeMillis();
            hits = ragSearchTool.search(request.message(), agent.knowledgeScopes(), 5);
            traceRecorder.recordStage(
                    trace.traceId(),
                    "rag_search",
                    Map.of("hitCount", hits.size(), "scopes", agent.knowledgeScopes()),
                    System.currentTimeMillis() - ragStart
            );
        }

        checkCancelled(taskId);

        String fullText;
        Integer promptTokens = null;
        Integer completionTokens = null;
        long llmStart = System.currentTimeMillis();

        if (useMockLlm()) {
            fullText = streamMockResponse(emitter, taskId, request.message(), agent);
            promptTokens = estimateTokens(request.message());
            completionTokens = estimateTokens(fullText);
        } else {
            String apiKey = environment.getProperty("DASHSCOPE_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("DASHSCOPE_API_KEY not set, falling back to mock LLM");
                fullText = streamMockResponse(emitter, taskId, request.message(), agent);
                promptTokens = estimateTokens(request.message());
                completionTokens = estimateTokens(fullText);
            } else {
                DashScopeClient.LlmStreamResult result = streamDashScope(
                        emitter,
                        taskId,
                        apiKey,
                        agent,
                        request.message()
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
                        "regenerate", Boolean.TRUE.equals(request.regenerate()),
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
            AgentConfigSnapshot agent
    ) throws IOException {
        String response = "这是模拟回复（" + agent.name() + "）：" + userMessage;
        for (int i = 0; i < response.length(); i++) {
            checkCancelled(taskId);
            String delta = response.substring(i, i + 1);
            AguiSseWriter.send(emitter, "text", Map.of("delta", delta));
        }
        return response;
    }

    private boolean useMockLlm() {
        return llmProperties.isMock() || environment.matchesProfiles("test");
    }

    private String resolveConversationId(AguiRunRequest request, String userId, String agentCode) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("未登录，无法创建会话");
        }
        if (request.conversationId() != null && !request.conversationId().isBlank()
                && conversationService.get(userId, request.conversationId()).isPresent()) {
            return request.conversationId();
        }
        return conversationService.create(userId, agentCode).id();
    }

    private String resolveAgentCode(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return DEFAULT_AGENT;
        }
        return agentCode;
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
