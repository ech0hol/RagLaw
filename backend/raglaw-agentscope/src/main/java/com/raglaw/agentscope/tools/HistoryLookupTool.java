package com.raglaw.agentscope.tools;

import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.chat.dto.MessageDto;
import com.raglaw.chat.service.ConversationService;
import com.raglaw.memory.casefile.CaseScope;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class HistoryLookupTool implements AgentTool {
    public static final String TOOL_NAME = "history_lookup";
    private static final Logger log = LoggerFactory.getLogger(HistoryLookupTool.class);
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_EXCERPT_CHARS = 2_000;

    private final ConversationService conversationService;

    public HistoryLookupTool(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public String getName() { return TOOL_NAME; }

    @Override
    public String getDescription() {
        return "按当前案件范围读取已授权的历史消息片段；只接受已有来源 ID，不允许按自由文本搜索。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> ids = new HashMap<>();
        ids.put("type", "array");
        ids.put("items", Map.of("type", "string"));
        ids.put("description", "来自当前案件记忆的 sourceId 列表");
        Map<String, Object> limit = new HashMap<>();
        limit.put("type", "integer");
        limit.put("description", "最多返回条数，默认 5");
        return Map.of("type", "object", "properties", Map.of("sourceIds", ids, "limit", limit),
                "required", List.of("sourceIds"));
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        AgentRunSession session = param.getRuntimeContext().get(AgentRunSession.class);
        if (session == null || session.caseScope() == null) {
            return Mono.just(ToolResultBlock.error("内部错误：缺少案件范围"));
        }
        List<String> sourceIds = stringList(param.getInput().get("sourceIds"));
        int limit = parseLimit(param.getInput().get("limit"));
        String text = lookup(session.caseScope(), sourceIds, limit);
        return Mono.just(ToolResultBlock.of(param.getToolUseBlock().getId(), TOOL_NAME,
                ToolResultBlock.text(text)));
    }

    String lookup(CaseScope scope, List<String> sourceIds, int limit) {
        if (sourceIds == null || sourceIds.isEmpty()) return "未提供可授权的来源 ID。";
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        StringBuilder result = new StringBuilder("历史消息片段（仅当前案件授权范围）：\n");
        int unauthorized = 0;
        int returned = 0;
        for (String sourceId : sourceIds) {
            if (returned >= boundedLimit) break;
            MessageDto message = conversationService.findMessageInCase(
                    scope.userId(), scope.caseId(), sourceId).orElse(null);
            if (message == null) {
                unauthorized++;
                continue;
            }
            String excerpt = message.content() == null ? "" : message.content();
            if (excerpt.length() > MAX_EXCERPT_CHARS) excerpt = excerpt.substring(0, MAX_EXCERPT_CHARS) + "…";
            result.append("[sourceId=").append(sourceId).append("] ")
                    .append(excerpt).append('\n');
            returned++;
        }
        if (unauthorized > 0) log.warn("History lookup denied sourceCount={} caseId={}", unauthorized, scope.caseId());
        if (returned == 0) return "当前案件没有可访问的历史片段。";
        return result.toString().trim();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : values) if (item != null && !String.valueOf(item).isBlank()) result.add(String.valueOf(item));
        return result;
    }

    private static int parseLimit(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? DEFAULT_LIMIT : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return DEFAULT_LIMIT; }
    }
}
