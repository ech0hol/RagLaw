package com.raglaw.agentscope.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.agentscope.agui.ReferencePayloadBuilder;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.runtime.AgentRunSession;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import com.raglaw.rag.tool.RagSearchTool;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.tool.ToolCallParam;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentscopeRagSearchToolTest {

    @Mock
    private RagSearchTool ragSearchTool;

    @Mock
    private ReferencePayloadBuilder referencePayloadBuilder;

    private AgentscopeRagSearchTool tool;

    @BeforeEach
    void setUp() {
        when(referencePayloadBuilder.relevantCitableHits(any(), any()))
                .thenAnswer(invocation -> ReferencePayloadBuilder.citableHits(invocation.getArgument(0)));
        tool = new AgentscopeRagSearchTool(ragSearchTool, referencePayloadBuilder);
    }

    @Test
    void callAsyncUsesExpertScopesAndCollectsHits() {
        ExpertContext expert = new ExpertContext(
                "STATUTE",
                "法规助手",
                "sys",
                List.of("STATUTE_CIVIL"),
                "STATUTE",
                "rule:STATUTE",
                false,
                false,
                null,
                true,
                List.of("rag_search"),
                List.of()
        );
        AgentRunSession session = new AgentRunSession("task-1");
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .put(ExpertContext.class, expert)
                .put(AgentRunSession.class, session)
                .build();

        when(ragSearchTool.searchDetailed(
                eq("拖欠工资"),
                eq(List.of("STATUTE_CIVIL")),
                eq(5),
                eq("STATUTE"),
                eq(null),
                eq(false),
                eq("rule:STATUTE")
        )).thenReturn(new RagSearchResult(
                List.of(new RagSearchHit("c1", "d1", 0.8, "/STATUTE", "excerpt")),
                null,
                Map.of()
        ));

        ToolUseBlock useBlock = ToolUseBlock.builder()
                .id("call-1")
                .name(AgentscopeRagSearchTool.TOOL_NAME)
                .input(Map.of("query", "拖欠工资"))
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(useBlock)
                .input(Map.of("query", "拖欠工资"))
                .runtimeContext(runtimeContext)
                .build();

        ToolResultBlock result = tool.callAsync(param).block();
        assertEquals(1, session.hits().size());
        assertTrue(result != null && !result.getOutput().isEmpty());
        verify(ragSearchTool).searchDetailed(
                "拖欠工资",
                List.of("STATUTE_CIVIL"),
                5,
                "STATUTE",
                null,
                false,
                "rule:STATUTE"
        );
    }

    @Test
    void callAsyncRunsNewSearchWhenToolQueryDiffersFromUserMessageAfterPresearch() {
        ExpertContext expert = new ExpertContext(
                "STATUTE",
                "法规助手",
                "sys",
                List.of("STATUTE_CRIMINAL"),
                "STATUTE",
                "rule:STATUTE",
                false,
                false,
                null,
                true,
                List.of("rag_search"),
                List.of()
        );
        AgentRunSession session = new AgentRunSession("task-2");
        session.setUserMessage("刑法中刑罚有哪些种类");
        session.addHits(List.of(new RagSearchHit("c31", "d1", 0.7, "/STATUTE", "第三十一条 单位犯罪")));
        session.markPresearchCompleted();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .put(ExpertContext.class, expert)
                .put(AgentRunSession.class, session)
                .build();

        when(ragSearchTool.searchDetailed(
                eq("附加刑 第三十四条"),
                eq(List.of("STATUTE_CRIMINAL")),
                eq(5),
                eq("STATUTE"),
                eq(null),
                eq(false),
                eq("rule:STATUTE")
        )).thenReturn(new RagSearchResult(
                List.of(new RagSearchHit("c34", "d1", 0.9, "/STATUTE", "第三十四条 附加刑的种类")),
                null,
                Map.of()
        ));

        ToolUseBlock useBlock = ToolUseBlock.builder()
                .id("call-2")
                .name(AgentscopeRagSearchTool.TOOL_NAME)
                .input(Map.of("query", "附加刑 第三十四条"))
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(useBlock)
                .input(Map.of("query", "附加刑 第三十四条"))
                .runtimeContext(runtimeContext)
                .build();

        ToolResultBlock result = tool.callAsync(param).block();
        assertTrue(result != null && !result.getOutput().isEmpty());
        assertEquals(2, session.hits().size());
        verify(ragSearchTool).searchDetailed(
                "附加刑 第三十四条",
                List.of("STATUTE_CRIMINAL"),
                5,
                "STATUTE",
                null,
                false,
                "rule:STATUTE"
        );
    }

    @Test
    void callAsyncReusesPresearchHitsWhenToolQueryMatchesUserMessage() {
        ExpertContext expert = new ExpertContext(
                "STATUTE",
                "法规助手",
                "sys",
                List.of("STATUTE_CRIMINAL"),
                "STATUTE",
                "rule:STATUTE",
                false,
                false,
                null,
                true,
                List.of("rag_search"),
                List.of()
        );
        AgentRunSession session = new AgentRunSession("task-3");
        session.setUserMessage("刑法中刑罚有哪些种类");
        session.addHits(List.of(new RagSearchHit("c31", "d1", 0.7, "/STATUTE", "第三十一条")));
        session.markPresearchCompleted();
        RuntimeContext runtimeContext = RuntimeContext.builder()
                .put(ExpertContext.class, expert)
                .put(AgentRunSession.class, session)
                .build();

        ToolUseBlock useBlock = ToolUseBlock.builder()
                .id("call-3")
                .name(AgentscopeRagSearchTool.TOOL_NAME)
                .input(Map.of("query", "刑法中刑罚有哪些种类"))
                .build();
        ToolCallParam param = ToolCallParam.builder()
                .toolUseBlock(useBlock)
                .input(Map.of("query", "刑法中刑罚有哪些种类"))
                .runtimeContext(runtimeContext)
                .build();

        ToolResultBlock result = tool.callAsync(param).block();
        assertTrue(result != null && !result.getOutput().isEmpty());
        assertEquals(1, session.hits().size());
        verify(ragSearchTool, org.mockito.Mockito.never()).searchDetailed(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void queryMatchesUserMessageTreatsWhitespaceAndSubstringAsEquivalent() {
        assertTrue(AgentscopeRagSearchTool.queryMatchesUserMessage(
                "刑法中刑罚有哪些种类",
                "刑法中刑罚有哪些种类"
        ));
        assertTrue(AgentscopeRagSearchTool.queryMatchesUserMessage(
                "刑罚有哪些种类",
                "刑法中刑罚有哪些种类"
        ));
        assertTrue(!AgentscopeRagSearchTool.queryMatchesUserMessage(
                "附加刑 第三十四条",
                "刑法中附加刑有哪些种类"
        ));
    }

    @Test
    void buildCatalogToolTextFormatsInventoryResults() {
        RagSearchResult result = new RagSearchResult(
                List.of(
                        new RagSearchHit(
                                "catalog-summary",
                                null,
                                1.0,
                                "/KNOWLEDGE",
                                "知识库概览：已入库法规 2 部",
                                "知识库概览：已入库法规 2 部",
                                "知识库概览",
                                "catalog-summary"
                        ),
                        new RagSearchHit(
                                "catalog-d1",
                                "d1",
                                1.0,
                                "/STATUTE/CIVIL",
                                "类型: 法规 | 分类: /STATUTE/CIVIL",
                                "类型: 法规 | 分类: /STATUTE/CIVIL",
                                "中华人民共和国民法典",
                                "catalog-d1"
                        )
                ),
                null,
                Map.of("catalogMode", true)
        );

        String text = tool.buildToolText(result, false, "目录");
        assertTrue(text.contains("知识库目录检索结果"));
        assertTrue(text.contains("中华人民共和国民法典"));
        assertTrue(text.contains("禁止补充未列出文档"));
        assertTrue(text.contains("[1]"));
        assertTrue(!text.contains("[2]"));
        assertTrue(text.contains("【知识库概览】"));
    }
}
