package com.raglaw.rag.retrieval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class QueryRewriteService {

    private static final Pattern FILLER_PATTERN = Pattern.compile(
            "我想|我要|请问|能否|可以|是否|是不是|怎么|如何|什么|哪些|吗|呢|啊|呀|的|了|？|\\?"
    );

    private static final List<TermExpansion> EXPANSIONS = List.of(
            new TermExpansion("化学品", "危险化学品"),
            new TermExpansion("化学", "危险化学品"),
            new TermExpansion("买", "购买"),
            new TermExpansion("卖", "销售"),
            new TermExpansion("违法", "违法 法律责任"),
            new TermExpansion("刑罚", "第三十三条 主刑 附加刑"),
            new TermExpansion("主刑", "第三十三条 主刑"),
            new TermExpansion("附加刑", "第三十四条 附加刑")
    );

    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String normalized = query.trim();
        Set<String> terms = new LinkedHashSet<>();
        String stripped = FILLER_PATTERN.matcher(normalized).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (!stripped.isBlank()) {
            terms.add(stripped);
        }
        for (TermExpansion expansion : EXPANSIONS) {
            if (normalized.contains(expansion.trigger())) {
                terms.add(expansion.expansion());
            }
        }
        if (terms.isEmpty()) {
            return normalized;
        }
        return String.join(" ", terms);
    }

    /**
     * Biases retrieval toward substantive article text when the first pass only hits TOC chunks.
     */
    public String rewriteForArticleRetrieval(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        Set<String> terms = new LinkedHashSet<>();
        String rewritten = rewrite(query);
        if (!rewritten.isBlank()) {
            terms.add(rewritten);
        }
        String normalized = query.trim();
        if (normalized.contains("刑罚") || normalized.contains("主刑") || normalized.contains("附加刑")) {
            terms.add("第三十三条 主刑 附加刑");
            terms.add("第三十四条 附加刑");
        }
        if (normalized.contains("刑法")) {
            terms.add("第三十三条");
        }
        if (terms.isEmpty()) {
            return normalized;
        }
        return String.join(" ", terms);
    }

    private record TermExpansion(String trigger, String expansion) {
    }
}
