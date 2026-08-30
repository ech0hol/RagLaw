package com.raglaw.rag.retrieval;

import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FullTextSnippetExtractor {

    private static final int CONTEXT_CHARS = 120;

    private FullTextSnippetExtractor() {
    }

    public static String extract(String fullText, String searchQuery, int maxChars) {
        return extractSkippingHeadings(fullText, searchQuery, maxChars);
    }

    public static String extractSkippingHeadings(String fullText, String searchQuery, int maxChars) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }
        String text = fullText.trim();
        if (searchQuery == null || searchQuery.isBlank()) {
            return truncate(text, maxChars);
        }
        List<String> terms = splitTerms(searchQuery);
        terms.sort(Comparator.comparingInt(String::length).reversed());
        for (String term : terms) {
            String snippet = extractAroundTerm(text, term, maxChars);
            if (!snippet.isBlank() && !ChunkHeadingHeuristics.looksLikeShortHeading(snippet)) {
                return snippet;
            }
        }
        return truncate(text, maxChars);
    }

    public static String extractAroundAnchor(String fullText, String anchor, int maxChars) {
        if (fullText == null || fullText.isBlank() || anchor == null || anchor.isBlank()) {
            return "";
        }
        String text = fullText.trim();
        for (String variant : expandArticleAnchorVariants(anchor)) {
            int index = findTerm(text, variant);
            if (index >= 0) {
                return buildSnippet(text, index, maxChars);
            }
        }
        return "";
    }

    private static List<String> expandArticleAnchorVariants(String anchor) {
        List<String> variants = new ArrayList<>();
        if (anchor != null && !anchor.isBlank()) {
            variants.add(anchor.trim());
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("第(\\d+)条").matcher(anchor == null ? "" : anchor);
        if (matcher.find()) {
            String chinese = arabicToChineseArticleNumber(matcher.group(1));
            if (!chinese.isBlank()) {
                variants.add("第" + chinese + "条");
            }
        }
        return variants;
    }

    private static String arabicToChineseArticleNumber(String digits) {
        if (digits == null || digits.isBlank()) {
            return "";
        }
        try {
            int value = Integer.parseInt(digits);
            if (value <= 0) {
                return "";
            }
            return toChineseNumber(value);
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static String toChineseNumber(int value) {
        if (value <= 0) {
            return "";
        }
        String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (value < 10) {
            return digits[value];
        }
        if (value < 20) {
            return "十" + (value == 10 ? "" : digits[value - 10]);
        }
        if (value < 100) {
            int tens = value / 10;
            int ones = value % 10;
            return digits[tens] + "十" + (ones == 0 ? "" : digits[ones]);
        }
        if (value < 1000) {
            int hundreds = value / 100;
            int remainder = value % 100;
            StringBuilder builder = new StringBuilder(digits[hundreds]).append("百");
            if (remainder == 0) {
                return builder.toString();
            }
            if (remainder < 10) {
                return builder.append("零").append(digits[remainder]).toString();
            }
            return builder.append(toChineseNumber(remainder)).toString();
        }
        return String.valueOf(value);
    }

    public static String extractAroundArticles(String fullText, List<String> anchors, int maxChars) {
        if (fullText == null || fullText.isBlank() || anchors == null || anchors.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int perAnchor = Math.max(80, maxChars / anchors.size());
        for (String anchor : anchors) {
            if (anchor == null || anchor.isBlank()) {
                continue;
            }
            String snippet = extractAroundAnchor(fullText, anchor, perAnchor);
            if (snippet.isBlank()
                    || ChunkHeadingHeuristics.looksLikeShortHeading(snippet)
                    || ChunkHeadingHeuristics.looksLikeTableOfContents(snippet)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(snippet);
        }
        return truncate(builder.toString(), maxChars);
    }

    private static String extractAroundTerm(String text, String term, int maxChars) {
        int searchFrom = 0;
        int resumeFrom = -1;
        while (searchFrom < text.length()) {
            int index = findTermFrom(text, term, searchFrom);
            if (index < 0) {
                break;
            }
            int lineStart = text.lastIndexOf('\n', index);
            lineStart = lineStart < 0 ? 0 : lineStart + 1;
            int lineEnd = text.indexOf('\n', index);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String matchedLine = text.substring(lineStart, lineEnd).trim();
            if (ChunkHeadingHeuristics.looksLikeShortHeading(matchedLine)
                    || ChunkHeadingHeuristics.looksLikeChapterHeading(matchedLine)) {
                resumeFrom = lineEnd + 1;
                searchFrom = resumeFrom;
                continue;
            }
            String snippet = buildSnippet(text, index, maxChars);
            if (!ChunkHeadingHeuristics.looksLikeShortHeading(snippet)) {
                return snippet;
            }
            searchFrom = index + Math.max(1, term.length());
        }
        if (resumeFrom >= 0 && resumeFrom < text.length()) {
            return buildSnippetFrom(text, resumeFrom, maxChars);
        }
        return "";
    }

    private static String buildSnippetFrom(String text, int startIndex, int maxChars) {
        int end = Math.min(text.length(), startIndex + maxChars + CONTEXT_CHARS);
        return truncate(text.substring(startIndex, end).trim(), maxChars);
    }

    private static String buildSnippet(String text, int index, int maxChars) {
        int start = Math.max(0, index - CONTEXT_CHARS);
        int end = Math.min(text.length(), index + CONTEXT_CHARS);
        String snippet = text.substring(start, end).trim();
        if (start > 0) {
            snippet = "…" + snippet;
        }
        if (end < text.length()) {
            snippet = snippet + "…";
        }
        return truncate(snippet, maxChars);
    }

    private static List<String> splitTerms(String query) {
        String normalized = query.trim().replaceAll("\\s+", " ");
        List<String> terms = new ArrayList<>();
        for (String part : normalized.split(" ")) {
            if (!part.isBlank() && part.length() >= 2) {
                terms.add(part);
            }
        }
        if (terms.isEmpty()) {
            terms.add(normalized);
        }
        return terms;
    }

    private static int findTerm(String text, String term) {
        return findTermFrom(text, term, 0);
    }

    private static int findTermFrom(String text, String term, int fromIndex) {
        if (term == null || term.isBlank()) {
            return -1;
        }
        int index = text.indexOf(term, fromIndex);
        if (index >= 0) {
            return index;
        }
        index = text.toLowerCase().indexOf(term.toLowerCase(), fromIndex);
        if (index >= 0) {
            return index;
        }
        if (term.length() >= 2) {
            for (int i = fromIndex; i <= text.length() - term.length(); i++) {
                if (text.regionMatches(true, i, term, 0, term.length())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String truncate(String content, int maxChars) {
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}
