package com.raglaw.agentscope.tools;

import com.raglaw.agentscope.agui.ReferencePayloadBuilder;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.rag.tool.CatalogQueryDetector;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import com.raglaw.rag.tool.RagSearchTool;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class AgentscopeRagSearchTool implements AgentTool {

    public static final String TOOL_NAME = "rag_search";

    private static final int DEFAULT_LIMIT = 5;

    private final RagSearchTool ragSearchTool;
    private final ReferencePayloadBuilder referencePayloadBuilder;

    public AgentscopeRagSearchTool(
            RagSearchTool ragSearchTool,
            ReferencePayloadBuilder referencePayloadBuilder
    ) {
        this.ragSearchTool = ragSearchTool;
        this.referencePayloadBuilder = referencePayloadBuilder;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "检索法律知识库中的法规、案例片段。回答法律问题前必须先调用此工具。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> queryProp = new HashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "检索关键词或问题表述");

        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "返回条数上限，默认 5");

        Map<String, Object> properties = new HashMap<>();
        properties.put("query", queryProp);
        properties.put("limit", limitProp);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        ExpertContext expert = param.getRuntimeContext().get(ExpertContext.class);
        AgentRunSession session = param.getRuntimeContext().get(AgentRunSession.class);
        if (expert == null || session == null) {
            return Mono.just(ToolResultBlock.error("内部错误：缺少专家运行上下文"));
        }
        if (session.isCancelled()) {
            return Mono.just(ToolResultBlock.error("对话已取消"));
        }

        String query = stringArg(param.getInput(), "query");
        if (query.isBlank()) {
            return Mono.just(ToolResultBlock.error("query 不能为空"));
        }
        if (session.userMessage() != null
                && CatalogQueryDetector.isCatalogQuery(session.userMessage())) {
            query = session.userMessage();
        }

        int limit = intArg(param.getInput(), "limit", DEFAULT_LIMIT);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        boolean catalogQuery = CatalogQueryDetector.isCatalogQuery(query);
        if (catalogQuery && !session.hits().isEmpty()) {
            RagSearchResult existing = new RagSearchResult(
                    session.hits(),
                    session.lastFunnelResult(),
                    session.lastRetrievalTrace()
            );
            String toolText = buildToolText(existing, expert.lowConfidence(), session.userMessage());
            return Mono.just(ToolResultBlock.of(
                    param.getToolUseBlock().getId(),
                    TOOL_NAME,
                    ToolResultBlock.text(toolText)
            ));
        }
        if (!catalogQuery
                && session.presearchCompleted()
                && !session.hits().isEmpty()
                && queryMatchesUserMessage(query, session.userMessage())) {
            RagSearchResult existing = new RagSearchResult(
                    session.hits(),
                    session.lastFunnelResult(),
                    session.lastRetrievalTrace()
            );
            String toolText = buildToolText(existing, expert.lowConfidence(), session.userMessage());
            return Mono.just(ToolResultBlock.of(
                    param.getToolUseBlock().getId(),
                    TOOL_NAME,
                    ToolResultBlock.text(toolText)
            ));
        }
        if (catalogQuery) {
            limit = Math.max(limit, 30);
        }
        if (limit > 30) {
            limit = 30;
        } else if (!catalogQuery && limit > 10) {
            limit = 10;
        }

        long startMs = System.currentTimeMillis();
        RagSearchResult searchResult = ragSearchTool.searchDetailed(
                query,
                expert.knowledgeScopes(),
                limit,
                expert.searchAgentCode(),
                expert.contextDocumentId(),
                expert.lowConfidence(),
                expert.routeReason()
        );
        session.recordSearch(searchResult, System.currentTimeMillis() - startMs);

        String toolText = buildToolText(searchResult, expert.lowConfidence(), session.userMessage());
        return Mono.just(ToolResultBlock.of(
                param.getToolUseBlock().getId(),
                TOOL_NAME,
                ToolResultBlock.text(toolText)
        ));
    }

    String buildToolText(RagSearchResult searchResult, boolean lowConfidence, String userQuery) {
        if (searchResult != null
                && Boolean.TRUE.equals(searchResult.retrievalTrace().get("catalogMode"))) {
            return buildCatalogToolText(searchResult.hits());
        }
        return buildToolText(searchResult == null ? List.of() : searchResult.hits(), lowConfidence, userQuery);
    }

    static String buildCatalogToolText(List<RagSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "知识库当前没有可查询的法规或案例文档。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("知识库目录检索结果（以下为全部可查询范围，禁止补充未列出文档）：\n");
        for (RagSearchHit hit : hits) {
            if (!ReferencePayloadBuilder.isUserVisibleHit(hit)) {
                sb.append("【知识库概览】\n").append(hit.llmContentOrExcerpt()).append('\n');
            }
        }
        List<RagSearchHit> citable = ReferencePayloadBuilder.citableHits(hits);
        for (int i = 0; i < citable.size(); i++) {
            RagSearchHit hit = citable.get(i);
            sb.append('[').append(i + 1).append("] ");
            if (hit.title() != null && !hit.title().isBlank()) {
                sb.append(hit.title());
            }
            if (hit.l1L2L3Path() != null && !hit.l1L2L3Path().isBlank()) {
                sb.append(" · ").append(hit.l1L2L3Path());
            }
            sb.append('\n').append(hit.llmContentOrExcerpt()).append('\n');
        }
        sb.append("说明：概览行不可引用；脚注 [n] 仅对应上方文档条目。\n");
        return sb.toString().trim();
    }

    String buildToolText(List<RagSearchHit> hits, boolean lowConfidence, String userQuery) {
        List<RagSearchHit> citable = referencePayloadBuilder.relevantCitableHits(hits, userQuery);
        if (citable.isEmpty()) {
            if (lowConfidence) {
                return "当前知识库未检索到足够依据。检索结果可能与问题不完全匹配，请说明无法基于文档作答，不要编造法条。";
            }
            return "当前知识库未检索到足够依据，请说明无法基于文档作答，不要编造法条。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("检索到 ").append(citable.size()).append(" 条依据：\n");
        for (int i = 0; i < citable.size(); i++) {
            RagSearchHit hit = citable.get(i);
            sb.append('[').append(i + 1).append("] ");
            if (hit.title() != null && !hit.title().isBlank()) {
                sb.append('《').append(hit.title()).append('》');
            }
            if (hit.l1L2L3Path() != null && !hit.l1L2L3Path().isBlank()) {
                sb.append(" · ").append(hit.l1L2L3Path());
            }
            sb.append('\n').append(hit.llmContentOrExcerpt()).append('\n');
        }
        if (lowConfidence) {
            sb.append("注意：检索结果可能与问题不完全匹配，请如实说明局限，不要编造法条。\n");
        }
        return sb.toString().trim();
    }

    private static String stringArg(Map<String, Object> input, String key) {
        if (input == null) {
            return "";
        }
        Object value = input.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    static boolean queryMatchesUserMessage(String query, String userMessage) {
        if (query == null || query.isBlank() || userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String normalizedQuery = normalizeQueryForMatch(query);
        String normalizedUser = normalizeQueryForMatch(userMessage);
        if (normalizedQuery.equals(normalizedUser)) {
            return true;
        }
        return normalizedQuery.contains(normalizedUser) || normalizedUser.contains(normalizedQuery);
    }

    static String normalizeQueryForMatch(String text) {
        return text.trim().replaceAll("\\s+", "");
    }

    private static int intArg(Map<String, Object> input, String key, int defaultValue) {
        if (input == null || !input.containsKey(key)) {
            return defaultValue;
        }
        Object value = input.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
