package com.raglaw.agentscope.mcp;

public final class WebSearchPolicy {

    private WebSearchPolicy() {
    }

    public static final String PROMPT_SECTION = """
            联网搜索策略：
            - 法律问题必须先调用 rag_search 检索知识库。
            - 当 rag_search 无结果、lowConfidence，或问题涉及「最新」「今年」「新闻」「官网」「刚刚出台」等时效信息时，可调用 tavily-search。
            - 知识库已有明确法条时，联网结果仅作补充，不得替代法条作答。
            """;
}
