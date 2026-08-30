package com.raglaw.agentscope.runtime;

import com.raglaw.agentscope.config.AgentscopeMcpProperties;
import com.raglaw.agentscope.agui.RagCitationInstructions;
import com.raglaw.agentscope.expert.ExpertContext;
import com.raglaw.agentscope.mcp.McpClientFactory;
import com.raglaw.agentscope.mcp.WebSearchPolicy;
import com.raglaw.agentscope.tools.AgentscopeRagSearchTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentRunFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentRunFactory.class);

    private static final String RAG_TOOL_INSTRUCTION = """
            工具使用要求：
            - 若用户消息已包含【当前合同】与【已识别审查意见】，优先基于该上下文作答，可辅以 rag_search 检索法规依据。
            - 回答法律问题前，必须先调用 rag_search 工具检索知识库（若已启用）。
            - 若用户问「有哪些法规/案例可查询」「知识库范围」「能查什么」，必须先调用 rag_search；回答只能列出检索结果中的文档名称，禁止用模型记忆补充未入库法规。
            - 当 rag_search 返回 0 条或提示「未检索到足够依据」时，只能说明知识库局限，禁止写「根据检索结果」或列举任何法规名称。
            - 当 rag_search 返回 ≥1 条可引用依据时，禁止在回答中出现「未检索到」「知识库未检索」「无法基于文档作答」等否定表述。
            - 根据检索结果组织回答；无检索结果时如实说明知识库局限，不要编造法条。
            - 回答结构：首段一句话结论；分节用编号小标题与要点列表；
            """
            + RagCitationInstructions.TOOL_USAGE
            + """
            - 禁止输出检索/生成过程描述（如「正在检索…」「正在分析…」）。
            """;

    private final AgentscopeRagSearchTool ragSearchTool;
    private final McpClientFactory mcpClientFactory;
    private final AgentscopeMcpProperties mcpProperties;

    public AgentRunFactory(
            AgentscopeRagSearchTool ragSearchTool,
            McpClientFactory mcpClientFactory,
            AgentscopeMcpProperties mcpProperties
    ) {
        this.ragSearchTool = ragSearchTool;
        this.mcpClientFactory = mcpClientFactory;
        this.mcpProperties = mcpProperties;
    }

    public ReActAgent build(ExpertContext expert, String model, String apiKey) {
        Toolkit toolkit = buildToolkit(expert);

        String modelName = normalizeModel(model);
        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .build();

        String sysPrompt = expert.systemPrompt() + "\n\n" + RAG_TOOL_INSTRUCTION;
        if (hasWebSearch(expert)) {
            sysPrompt = sysPrompt + "\n\n" + WebSearchPolicy.PROMPT_SECTION;
        }

        return ReActAgent.builder()
                .name(expert.searchAgentCode())
                .sysPrompt(sysPrompt)
                .model(chatModel)
                .toolkit(toolkit)
                .maxIters(6)
                .build();
    }

    private Toolkit buildToolkit(ExpertContext expert) {
        Toolkit toolkit = new Toolkit();
        List<String> tools = expert.tools() == null ? List.of() : expert.tools();
        List<String> mcpServers = expert.mcpServers() == null ? List.of() : expert.mcpServers();

        if (tools.contains(AgentscopeRagSearchTool.TOOL_NAME)) {
            toolkit.registerAgentTool(ragSearchTool);
        }

        if (mcpProperties.isEnabled()
                && mcpServers.contains("tavily")
                && tools.contains("tavily-search")) {
            mcpClientFactory.buildTavilyClient().ifPresent(client -> {
                try {
                    toolkit.registerMcpClient(client).block();
                } catch (Exception e) {
                    log.warn("Failed to register MCP client: {}", e.getMessage());
                    client.close();
                }
            });
        }

        filterToolkitTools(toolkit, tools);

        if (toolkit.getToolNames().isEmpty()) {
            log.warn("Agent {} has no tools enabled in configuration", expert.searchAgentCode());
        }
        return toolkit;
    }

    private static void filterToolkitTools(Toolkit toolkit, List<String> allowedTools) {
        Set<String> allowed = new HashSet<>(allowedTools);
        for (String toolName : toolkit.getToolNames()) {
            if (!allowed.contains(toolName)) {
                toolkit.removeTool(toolName);
            }
        }
    }

    private static boolean hasWebSearch(ExpertContext expert) {
        List<String> tools = expert.tools() == null ? List.of() : expert.tools();
        List<String> mcpServers = expert.mcpServers() == null ? List.of() : expert.mcpServers();
        return tools.contains("tavily-search") && mcpServers.contains("tavily");
    }

    private static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return "qwen-plus";
        }
        if (model.startsWith("dashscope:")) {
            return model.substring("dashscope:".length());
        }
        return model;
    }
}
