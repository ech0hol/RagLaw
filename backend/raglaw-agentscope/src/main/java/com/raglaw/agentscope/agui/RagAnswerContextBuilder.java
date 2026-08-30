package com.raglaw.agentscope.agui;

import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RagAnswerContextBuilder {

    private static final Pattern RETRIEVAL_STATUS_PREFIX = Pattern.compile(
            "^\\s*正在(?:检索|查询|搜索|分析|整理).{0,120}?[。…\\.]{1,3}\\s*",
            Pattern.MULTILINE
    );

    private RagAnswerContextBuilder() {
    }

    public static String build(
            List<RagSearchHit> hits,
            String delegatedPeer,
            boolean lowConfidence
    ) {
        if (hits == null || hits.isEmpty()) {
            if (lowConfidence) {
                return """
                        未检索到与问题高度匹配的依据。
                        回答要求：明确说明知识库局限，不要编造法律依据。
                        """;
            }
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (delegatedPeer != null && !delegatedPeer.isBlank()) {
            sb.append("已通过专家助手「").append(delegatedPeer).append("」检索到参考依据。\n");
        } else {
            sb.append("以下是从知识库检索到的参考条文。\n");
        }
        if (lowConfidence) {
            sb.append("注意：以下检索结果可能与问题不完全匹配，请如实说明局限，不要编造法条。\n");
        }
        sb.append("""
                回答要求：
                - 回答结构（Markdown）：
                """
                + RagCitationInstructions.ANSWER_STRUCTURE
                + """
                不要先列出依据清单或摘录全文，直接组织回答。

                示例输出格式：
                单纯购买化学品本身不违法，但是否需许可视化学品是否列入目录而定。

                1. **目录内化学品需许可**
                - 列入《危险化学品目录》的化学品，购买需遵守许可制度。
                - 依据 **《危险化学品安全管理条例》第五十三条**[1]。

                2. **一般情形**
                - 未列入目录的化学品，购买行为本身不构成违法。
                """);
        List<RagSearchHit> citable = ReferencePayloadBuilder.citableHits(hits);
        for (int i = 0; i < citable.size(); i++) {
            RagSearchHit hit = citable.get(i);
            sb.append('[').append(i + 1).append("] ");
            if (hit.title() != null && !hit.title().isBlank()) {
                sb.append('《').append(hit.title()).append("》");
            }
            if (hit.l1L2L3Path() != null && !hit.l1L2L3Path().isBlank()) {
                sb.append(" · ").append(hit.l1L2L3Path());
            }
            sb.append('\n').append(hit.llmContentOrExcerpt()).append('\n');
        }
        return sb.toString();
    }

    private static final Pattern CITED_FOOTNOTE_PATTERN =
            Pattern.compile("(?:\\*\\*《[^》]+》[^*]*\\*\\*|《[^》]+》)\\[\\d+]");
    private static final Pattern CONTRADICTORY_RETRIEVAL_DISCLAIMER = Pattern.compile(
            "(?<=[。！？\\n])[^。！？\\n]*(?:未检索到|知识库未检索|无法基于文档作答)[^。！？\\n]*[。！？]?\\s*$"
    );

    public static String postProcess(String answer) {
        return postProcess(answer, false);
    }

    private static final Pattern FIRST_NUMBERED_SECTION = Pattern.compile("(?m)^1\\.\\s+\\*\\*");

    public static String postProcess(String answer, boolean hasRetrievalEvidence) {
        if (answer == null || answer.isBlank()) {
            return "";
        }
        String normalized = normalizeAnswerMarkdown(answer).trim();
        String stripped = stripContradictoryRetrievalDisclaimer(normalized, hasRetrievalEvidence);
        return dedupeRepeatedAnswerBlocks(stripped);
    }

    static String dedupeRepeatedAnswerBlocks(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        Matcher matcher = FIRST_NUMBERED_SECTION.matcher(answer);
        int first = -1;
        int second = -1;
        while (matcher.find()) {
            if (first < 0) {
                first = matcher.start();
            } else {
                second = matcher.start();
                break;
            }
        }
        if (second < 0) {
            return answer;
        }
        return answer.substring(second).trim();
    }

    static String stripContradictoryRetrievalDisclaimer(String answer) {
        return stripContradictoryRetrievalDisclaimer(answer, false);
    }

    static String stripContradictoryRetrievalDisclaimer(String answer, boolean hasRetrievalEvidence) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        if (!CITED_FOOTNOTE_PATTERN.matcher(answer).find() && !hasRetrievalEvidence) {
            return answer;
        }
        String stripped = CONTRADICTORY_RETRIEVAL_DISCLAIMER.matcher(answer).replaceAll("").trim();
        return stripped.isBlank() ? answer.trim() : stripped;
    }

    static String normalizeAnswerMarkdown(String answer) {
        return indentNestedListUnderNumberedSections(stripOrphanFootnotes(stripRetrievalStatusPrefix(answer)
                .replaceAll("\\*\\*\\s*\\n\\s*《", "**《")
                .replaceAll("》\\s*\\n+\\s*([^*\\n]+?)\\s*\\n+\\s*\\*\\*", "》$1**")
                .replaceAll("》\\s*\\n\\s*\\*\\*", "》**")
                .replaceAll("\\*\\*\\s+(?=《)", "**")
                .replaceAll("(?<=》)\\s+\\*\\*", "**")
                .replaceAll("》\\s*\\n+\\s*\\[(\\d+)]", "》[$1]")
                .replaceAll("\\*\\*\\s*\\n+\\s*\\[(\\d+)]", "**[$1]")
                .replaceAll("\\n+\\s*([，。；：、])", "$1")
                .replaceAll(
                        "([，。；：、])\\s*\\n(?![\\n])(?!\\*\\*[一二三四五六七八九十百]+、)(?!\\d+\\.\\s)(?![一二三四五六七八九十百]+、)(?!\\s*[-*]\\s)(?!\\*\\s)",
                        "$1"
                )
                .replaceAll("(\\d+\\.\\s+\\*\\*[^*\\n]+?\\*\\*)\\s*\\n\\s*(-\\s)", "$1\n   $2")
                .replaceAll("(\\d+\\.\\s+\\*\\*[^*\\n]+?\\*\\*)\\s+(-\\s)", "$1\n   $2")
                .replaceAll("([。！？])\\s*(\\d+\\.\\s)", "$1\n\n$2")
                .replaceAll("([。！？])\\s*(首先|其次|此外|最后|综上)", "$1\n\n$2")
                .replaceAll("([。！？])\\s*(\\*\\*[一二三四五六七八九十百]+、[^*]*\\*\\*)", "$1\n\n$2")
                .replaceAll("([。！？])\\s*([一二三四五六七八九十百]+、)", "$1\n\n$2")
                .replaceAll("([。！？])\\s*(-\\s+\\*\\*)", "$1\n\n$2")
                .replaceAll("([。！？])\\s*\\n-\\s", "$1\n\n- ")
                .replaceAll("；\\s*-\\s", "；\n\n- ")));
    }

    static String indentNestedListUnderNumberedSections(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String[] lines = answer.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean underNumberedSection = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+\\.\\s+\\*\\*.*")) {
                underNumberedSection = true;
                sb.append(line);
            } else if (underNumberedSection && trimmed.startsWith("- ") && !line.startsWith("   ")) {
                sb.append("   ").append(trimmed);
            } else {
                if (underNumberedSection
                        && !trimmed.isEmpty()
                        && !trimmed.startsWith("- ")
                        && !trimmed.matches("^\\d+\\.\\s+.*")) {
                    underNumberedSection = false;
                }
                sb.append(line);
            }
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    static String stripRetrievalStatusPrefix(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String stripped = RETRIEVAL_STATUS_PREFIX.matcher(answer).replaceFirst("");
        return stripped.isBlank() ? answer.trim() : stripped;
    }

    private static final Pattern CODE_SEGMENT_PATTERN = Pattern.compile("```[\\s\\S]*?```|`[^`\\n]+`");

    static String stripOrphanFootnotes(String answer) {
        Matcher matcher = CODE_SEGMENT_PATTERN.matcher(answer);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        while (matcher.find()) {
            sb.append(stripFootnotesInText(answer.substring(pos, matcher.start())));
            sb.append(matcher.group());
            pos = matcher.end();
        }
        sb.append(stripFootnotesInText(answer.substring(pos)));
        return sb.toString();
    }

    private static String stripFootnotesInText(String text) {
        return text.replaceAll("(?<![》*])\\[(\\d+)]", "");
    }
}
