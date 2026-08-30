package com.raglaw.rag.retrieval.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChunkHeadingHeuristics {

    private static final Pattern ARTICLE_ANCHOR_PATTERN = Pattern.compile("第[^\\s，。；：、]{1,12}条");
    private static final Pattern CHAPTER_OR_SECTION_LINE = Pattern.compile(
            "^第[^\\s，。；：、]{1,12}[章节编].*$"
    );

    private static final int SHORT_HEADING_MAX_CHARS = 30;

    private ChunkHeadingHeuristics() {
    }

    public static boolean looksLikeShortHeading(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.length() > SHORT_HEADING_MAX_CHARS) {
            return false;
        }
        if (trimmed.contains("。") || trimmed.contains("；") || trimmed.contains("：")) {
            return false;
        }
        if (trimmed.startsWith("第") && trimmed.contains("章")) {
            return true;
        }
        if (trimmed.startsWith("第") && trimmed.contains("条")) {
            int articleEnd = trimmed.indexOf('条');
            if (articleEnd >= 0 && articleEnd + 1 < trimmed.length()) {
                String afterArticle = trimmed.substring(articleEnd + 1).trim();
                if (afterArticle.length() > 4) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean looksLikeChapterHeading(String text) {
        String trimmed = text == null ? "" : text.trim();
        return !trimmed.isEmpty()
                && trimmed.length() <= SHORT_HEADING_MAX_CHARS
                && trimmed.startsWith("第")
                && trimmed.contains("章");
    }

    public static boolean containsSubstantiveArticleBody(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        if (!trimmed.contains("条")) {
            return false;
        }
        return !looksLikeShortHeading(trimmed);
    }

    /**
     * Detects multi-line chapter/section heading lists (TOC) without substantive article text.
     */
    public static boolean looksLikeTableOfContents(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (containsSubstantiveArticleBody(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("目录") || trimmed.startsWith("## 目录")) {
            return true;
        }
        String[] lines = trimmed.split("\\R");
        int chapterSectionLines = 0;
        for (String line : lines) {
            String normalized = line.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (CHAPTER_OR_SECTION_LINE.matcher(normalized).matches()
                    || looksLikeChapterHeading(normalized)) {
                chapterSectionLines++;
            }
        }
        return chapterSectionLines >= 2;
    }

    public static String extractChapterAnchor(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        int chapterIndex = trimmed.indexOf('章');
        if (chapterIndex <= 0 || !trimmed.startsWith("第")) {
            return "";
        }
        return trimmed.substring(0, chapterIndex + 1).trim();
    }

    public static String extractArticleAnchor(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = ARTICLE_ANCHOR_PATTERN.matcher(text.trim());
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    public static List<String> extractArticleAnchors(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> anchors = new ArrayList<>();
        Matcher matcher = ARTICLE_ANCHOR_PATTERN.matcher(text.trim());
        while (matcher.find()) {
            anchors.add(matcher.group());
        }
        return anchors;
    }
}
