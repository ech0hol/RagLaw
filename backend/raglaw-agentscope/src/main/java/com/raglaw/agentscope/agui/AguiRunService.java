package com.raglaw.agentscope.agui;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.agentscope.agui.dto.AguiRunRequest;
import com.raglaw.agentscope.trace.TraceContext;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.agentscope.memory.MemoryCoordinator;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.chat.dto.MessageDto;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.common.auth.CurrentUserHolder;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AguiRunService {

    private static final Logger log = LoggerFactory.getLogger(AguiRunService.class);
    private static final String DEFAULT_AGENT = "GENERAL";

    private final AgentRegistry agentRegistry;
    private final TraceRecorder traceRecorder;
    private final ConversationService conversationService;
    private final TaskCancellationRegistry cancellationRegistry;
    private final Environment environment;
    private final AguiReactRunFacade reactRunFacade;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private MemoryCoordinator memoryCoordinator;

    public AguiRunService(
            AgentRegistry agentRegistry,
            TraceRecorder traceRecorder,
            ConversationService conversationService,
            TaskCancellationRegistry cancellationRegistry,
            Environment environment,
            AguiReactRunFacade reactRunFacade
    ) {
        this.agentRegistry = agentRegistry;
        this.traceRecorder = traceRecorder;
        this.conversationService = conversationService;
        this.cancellationRegistry = cancellationRegistry;
        this.environment = environment;
        this.reactRunFacade = reactRunFacade;
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
                failRun(emitter, e);
            } finally {
                cancellationRegistry.unregister(taskId);
            }
            return emitter;
        }
        executor.execute(() -> {
            try {
                executeRun(emitter, taskId, request, userId);
            } catch (Exception e) {
                failRun(emitter, e);
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
        String agentCode = resolveAgentCode(request, userId);
        AgentConfigSnapshot agent = resolveAgent(agentCode);
        String conversationId = resolveConversationId(request, userId, agentCode);
        boolean regenerate = Boolean.TRUE.equals(request.regenerate());

        String userMessage;
        String userMessageId = null;
        if (regenerate) {
            String assistantMessageId = request.regenerateFromMessageId();
            if (assistantMessageId == null || assistantMessageId.isBlank()) {
                assistantMessageId = conversationService.findLastAssistantMessageId(userId, conversationId)
                        .orElse(null);
            }
            if (assistantMessageId == null || assistantMessageId.isBlank()) {
                throw new IllegalArgumentException("找不到可重新生成的助手消息");
            }
            userMessage = conversationService.prepareRegenerateFromAssistant(
                            userId,
                            conversationId,
                            assistantMessageId
                    )
                    .orElseThrow(() -> new IllegalArgumentException("找不到可重新生成的用户消息"));
        } else {
            userMessage = request.message();
            if (userMessage == null || userMessage.isBlank()) {
                throw new IllegalArgumentException("消息不能为空");
            }
            MessageDto persistedUserMessage = conversationService.appendMessage(userId, conversationId, "user", userMessage, null)
                    .orElseThrow(() -> new IllegalArgumentException("会话不存在或无权访问"));
            userMessageId = persistedUserMessage.id();
            if (memoryCoordinator != null) {
                String correctionSourceId = userMessageId;
                String correctionText = userMessage;
                conversationService.findCaseId(userId, conversationId).ifPresent(caseId ->
                        memoryCoordinator.processBeforeSnapshot(
                                new CaseScope("default", userId, caseId), correctionSourceId, correctionText, userId));
            }
        }

        TraceContext trace = traceRecorder.start(
                conversationId,
                userId,
                userMessage,
                agent.code()
        );

        String contextDocumentId = conversationService.findContextDocumentId(userId, conversationId).orElse(null);

        AguiSseWriter.send(emitter, "meta", Map.of(
                "taskId", taskId,
                "traceId", trace.traceId(),
                "messageId", trace.messageId(),
                "conversationId", conversationId,
                "agentCode", agent.code()
        ));

        checkCancelled(taskId);
        AguiSseWriter.send(emitter, "status", Map.of("message", "正在处理您的问题…"));

        reactRunFacade.executeRun(
                emitter,
                taskId,
                request,
                userId,
                agent,
                conversationId,
                userMessage,
                regenerate,
                trace,
                contextDocumentId
        );
        if (memoryCoordinator != null && userMessageId != null) {
            String sourceId = userMessageId;
            String ordinaryText = userMessage;
            conversationService.findCaseId(userId, conversationId).ifPresent(caseId ->
                    memoryCoordinator.queueOrdinary(
                            new CaseScope("default", userId, caseId), sourceId, ordinaryText, userId));
        }
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
        return conversationService.create(userId, agentCode, null).id();
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

    private void failRun(SseEmitter emitter, Throwable error) {
        log.error("AG-UI run failed", error);
        try {
            AguiSseWriter.send(emitter, "error", Map.of("message", toUserMessage(error)));
            emitter.complete();
        } catch (IOException io) {
            log.warn("Failed to send SSE error event", io);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // emitter may already be closed
            }
        }
    }

    private static String toUserMessage(Throwable error) {
        if (error instanceof TaskCancelledException) {
            return "对话已取消";
        }
        if (error instanceof IllegalArgumentException && error.getMessage() != null) {
            return error.getMessage();
        }
        if (isPersistenceOrSqlError(error)) {
            return "服务暂时异常，请稍后重试";
        }
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        if (root instanceof IOException) {
            return "大模型服务连接失败，请检查网络或 DASHSCOPE_API_KEY 后重试";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            if (looksLikeSqlError(error.getMessage())) {
                return "服务暂时异常，请稍后重试";
            }
            return error.getMessage();
        }
        return "对话处理失败，请重试";
    }

    private static boolean isPersistenceOrSqlError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataAccessException || current instanceof SQLException) {
                return true;
            }
            if (current.getMessage() != null && looksLikeSqlError(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean looksLikeSqlError(String message) {
        String lower = message.toLowerCase();
        return lower.contains("sql") || lower.contains("jdbc") || lower.contains("insert into");
    }

    static class TaskCancelledException extends RuntimeException {
        TaskCancelledException(String taskId) {
            super("Task cancelled: " + taskId);
        }
    }
}
