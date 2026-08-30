package com.raglaw.agentscope.agui;

import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.mcp.TavilyResultParser;
import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.agentscope.tools.AgentscopeRagSearchTool;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.rag.tool.CatalogQueryDetector;
import com.raglaw.rag.tool.RagSearchHit;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AguiEventBridge {

    private final ReferencePayloadBuilder referencePayloadBuilder;
    private final TavilyResultParser tavilyResultParser;
    private final TraceRecorder traceRecorder;

    public AguiEventBridge(
            ReferencePayloadBuilder referencePayloadBuilder,
            TavilyResultParser tavilyResultParser,
            TraceRecorder traceRecorder
    ) {
        this.referencePayloadBuilder = referencePayloadBuilder;
        this.tavilyResultParser = tavilyResultParser;
        this.traceRecorder = traceRecorder;
    }

    public AguiStreamState newStreamState() {
        return new AguiStreamState();
    }

    public void sendDelegatedStatus(SseEmitter emitter, ExpertContext expert) throws IOException {
        if (!expert.delegated()) {
            return;
        }
        AguiSseWriter.send(emitter, "status", Map.of(
                "message", delegatedStatusMessage(expert),
                "peerAgent", expert.peerCode()
        ));
    }

    public void onEvent(
            SseEmitter emitter,
            AgentEvent event,
            AguiStreamState state,
            AgentRunSession session
    ) throws IOException {
        AgentEventType type = event.getType();
        if (type == AgentEventType.TEXT_BLOCK_DELTA) {
            String delta = ((TextBlockDeltaEvent) event).getDelta();
            if (delta != null && !delta.isEmpty()) {
                state.append(delta);
                AguiSseWriter.send(emitter, "text", Map.of("delta", delta));
            }
        } else if (type == AgentEventType.TOOL_CALL_START) {
            String toolName = ((ToolCallStartEvent) event).getToolCallName();
            if (shouldResetTextOnToolCall(toolName)) {
                resetStreamedText(emitter, state);
            }
            if (AgentscopeRagSearchTool.TOOL_NAME.equals(toolName)) {
                AguiSseWriter.send(emitter, "status", Map.of("message", "正在检索知识库…"));
            } else if ("tavily-search".equals(toolName)) {
                state.markMcpToolStart(((ToolCallStartEvent) event).getToolCallId());
                AguiSseWriter.send(emitter, "status", Map.of("message", "正在联网检索…"));
            }
        } else if (type == AgentEventType.TOOL_RESULT_TEXT_DELTA) {
            ToolResultTextDeltaEvent deltaEvent = (ToolResultTextDeltaEvent) event;
            if (deltaEvent.getDelta() != null) {
                state.appendToolResult(deltaEvent.getToolCallId(), deltaEvent.getDelta());
            }
        } else if (type == AgentEventType.TOOL_RESULT_END) {
            handleToolResultEnd(emitter, (ToolResultEndEvent) event, state, session);
        } else if (type == AgentEventType.AGENT_RESULT) {
            String resultText = ((AgentResultEvent) event).getResult().getTextContent();
            if (resultText != null && !resultText.isBlank() && state.length() == 0) {
                state.append(resultText);
                emitTextChunks(emitter, resultText);
            }
        }
    }

    private void resetStreamedText(SseEmitter emitter, AguiStreamState state) throws IOException {
        if (state.length() == 0) {
            return;
        }
        state.resetText();
        AguiSseWriter.send(emitter, "text_reset", Map.of());
    }

    static boolean shouldResetTextOnToolCall(String toolName) {
        return AgentscopeRagSearchTool.TOOL_NAME.equals(toolName) || "tavily-search".equals(toolName);
    }

    private void emitTextChunks(SseEmitter emitter, String text) throws IOException {
        int chunkSize = 120;
        for (int i = 0; i < text.length(); i += chunkSize) {
            String delta = text.substring(i, Math.min(i + chunkSize, text.length()));
            AguiSseWriter.send(emitter, "text", Map.of("delta", delta));
        }
    }

    private void handleToolResultEnd(
            SseEmitter emitter,
            ToolResultEndEvent event,
            AguiStreamState state,
            AgentRunSession session
    ) throws IOException {
        if (AgentscopeRagSearchTool.TOOL_NAME.equals(event.getToolCallName())) {
            emitKnowledgeReferences(emitter, session, session.userMessage());
            return;
        }
        if (!"tavily-search".equals(event.getToolCallName())) {
            return;
        }
        String raw = state.toolResultText(event.getToolCallId());
        List<TavilyResultParser.ParsedWebHit> hits = tavilyResultParser.parse(raw);
        long durationMs = state.mcpToolDurationMs(event.getToolCallId());
        String traceId = session.traceId();
        if (traceId != null && !traceId.isBlank()) {
            traceRecorder.recordStage(
                    traceId,
                    "mcp_tool_call",
                    Map.of(
                            "tool", "tavily-search",
                            "hitCount", hits.size(),
                            "toolCallId", event.getToolCallId() == null ? "" : event.getToolCallId()
                    ),
                    durationMs
            );
        }
        int baseIndex = session.webReferences().size()
                + ReferencePayloadBuilder.countCitableKnowledgeHits(session.hits());
        for (int i = 0; i < hits.size(); i++) {
            TavilyResultParser.ParsedWebHit hit = hits.get(i);
            WebReference webRef = new WebReference(
                    baseIndex + i + 1,
                    "web-" + event.getToolCallId() + "-" + i,
                    hit.title(),
                    hit.url(),
                    hit.excerpt()
            );
            session.addWebReference(webRef);
            Map<String, Object> payload = referencePayloadBuilder.buildWebPayload(webRef);
            AguiSseWriter.send(emitter, "reference", payload);
        }
    }

    public void emitKnowledgeReferences(
            SseEmitter emitter,
            AgentRunSession session,
            String userQuery
    ) throws IOException {
        List<Map<String, Object>> visible = referencePayloadBuilder.buildUserVisiblePayloads(
                session.hits(),
                userQuery
        );
        int start = session.emittedReferenceCount();
        if (start >= visible.size()) {
            return;
        }
        for (int i = start; i < visible.size(); i++) {
            AguiSseWriter.send(emitter, "reference", visible.get(i));
        }
        session.setEmittedReferenceCount(visible.size());
    }

    private static String delegatedStatusMessage(ExpertContext expert) {
        String code = expert.peerCode();
        if (code.startsWith("STATUTE")) {
            return "正在咨询法规助手…";
        }
        if (code.startsWith("CASE")) {
            return "正在咨询案例助手…";
        }
        if (code.startsWith("CONTRACT")) {
            return "正在咨询合同审查助手…";
        }
        return "正在咨询" + expert.peerName() + "…";
    }

    public static final class AguiStreamState {
        private final StringBuilder textBuffer = new StringBuilder();
        private final Map<String, StringBuilder> toolResultBuffers = new HashMap<>();
        private final Map<String, Long> mcpToolStartMs = new HashMap<>();

        public void resetText() {
            textBuffer.setLength(0);
        }

        public void append(String delta) {
            textBuffer.append(delta);
        }

        public void markMcpToolStart(String toolCallId) {
            if (toolCallId != null && !toolCallId.isBlank()) {
                mcpToolStartMs.put(toolCallId, System.currentTimeMillis());
            }
        }

        public long mcpToolDurationMs(String toolCallId) {
            if (toolCallId == null) {
                return 0L;
            }
            Long start = mcpToolStartMs.get(toolCallId);
            if (start == null) {
                return 0L;
            }
            return Math.max(0L, System.currentTimeMillis() - start);
        }

        public void appendToolResult(String toolCallId, String delta) {
            toolResultBuffers.computeIfAbsent(toolCallId, ignored -> new StringBuilder()).append(delta);
        }

        public String toolResultText(String toolCallId) {
            StringBuilder buffer = toolResultBuffers.get(toolCallId);
            return buffer == null ? "" : buffer.toString();
        }

        public int length() {
            return textBuffer.length();
        }

        public String text() {
            return textBuffer.toString();
        }
    }
}
