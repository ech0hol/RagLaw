package com.raglaw.rag.ingest;

import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MarkdownChunker {

    public static final int DEFAULT_MICRO_MAX_CHARS = 300;

    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{2,3}\\s+.+");
    private static final Pattern ARTICLE_LINE_PATTERN = Pattern.compile("^第\\s*[一二三四五六七八九十百千零〇\\d]+\\s*条");
    private static final Pattern ARTICLE_SPLIT_PATTERN = Pattern.compile("(?=第\\s*[一二三四五六七八九十百千零〇\\d]+\\s*条)");

    private MarkdownChunker() {
    }

    public record ChunkDraft(String content, String localId, String parentLocalId, ChunkLevel level) {

        public boolean isParent() {
            return level == ChunkLevel.PARENT;
        }

        public boolean isChild() {
            return level == ChunkLevel.CHILD;
        }

        public boolean isMicro() {
            return level == ChunkLevel.MICRO;
        }
    }

    public static List<String> chunkByParagraph(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String normalized = markdown.replace("\r\n", "\n");
        String[] paragraphs = normalized.split("\n\\s*\n");
        List<String> chunks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(trimmed);
            }
        }
        if (chunks.isEmpty()) {
            chunks.add(normalized.trim());
        }
        return chunks;
    }

    public static List<ChunkDraft> chunkHierarchy(String markdown) {
        return chunkHierarchyWithMicro(markdown, DEFAULT_MICRO_MAX_CHARS);
    }

    public static List<ChunkDraft> chunkHierarchyWithMicro(String markdown, int microMaxChars) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String normalized = markdown.replace("\r\n", "\n");
        List<ChunkDraft> base;
        boolean hasHeaders = normalized.lines().anyMatch(line -> HEADER_PATTERN.matcher(line.trim()).matches());
        if (hasHeaders) {
            base = chunkByHeaders(normalized);
        } else if (hasArticleMarkers(normalized)) {
            base = chunkByArticles(normalized);
        } else {
            base = chunkFlatWithVirtualParents(normalized);
        }
        base = filterOrphanChapterParents(base);
        return expandMicroChunks(assignChildLocalIds(base), microMaxChars);
    }

    /**
     * Drops TOC-style chapter PARENT nodes that have no CHILD/MICRO descendants,
     * and duplicate chapter-heading PARENTs when a later PARENT with the same title has children.
     */
    public static List<ChunkDraft> filterOrphanChapterParents(List<ChunkDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> parentChildCounts = new HashMap<>();
        for (ChunkDraft draft : drafts) {
            if (draft.parentLocalId() != null) {
                parentChildCounts.merge(draft.parentLocalId(), 1, Integer::sum);
            }
        }
        Map<String, String> keptChapterParentByTitle = new HashMap<>();
        List<ChunkDraft> parents = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            if (draft.isParent()) {
                parents.add(draft);
            }
        }
        for (ChunkDraft parent : parents) {
            String content = parent.content() == null ? "" : parent.content().trim();
            if (!ChunkHeadingHeuristics.looksLikeChapterHeading(content)) {
                continue;
            }
            int childCount = parentChildCounts.getOrDefault(parent.localId(), 0);
            if (childCount == 0) {
                continue;
            }
            String existing = keptChapterParentByTitle.get(content);
            if (existing == null) {
                keptChapterParentByTitle.put(content, parent.localId());
            } else {
                int existingCount = parentChildCounts.getOrDefault(existing, 0);
                if (childCount > existingCount) {
                    keptChapterParentByTitle.put(content, parent.localId());
                }
            }
        }
        Map<String, String> duplicateParentRemap = new HashMap<>();
        List<ChunkDraft> filtered = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            if (!draft.isParent()) {
                filtered.add(draft);
                continue;
            }
            String content = draft.content() == null ? "" : draft.content().trim();
            int childCount = parentChildCounts.getOrDefault(draft.localId(), 0);
            if (childCount == 0) {
                continue;
            }
            if (ChunkHeadingHeuristics.looksLikeChapterHeading(content)) {
                String kept = keptChapterParentByTitle.get(content);
                if (kept != null && !kept.equals(draft.localId())) {
                    duplicateParentRemap.put(draft.localId(), kept);
                    continue;
                }
            }
            filtered.add(draft);
        }
        return remapParentRefs(filtered, duplicateParentRemap);
    }

    private static List<ChunkDraft> remapParentRefs(List<ChunkDraft> drafts, Map<String, String> parentRemap) {
        Map<String, String> parentLocalIds = new HashMap<>();
        for (ChunkDraft draft : drafts) {
            if (draft.isParent() && draft.localId() != null) {
                parentLocalIds.put(draft.localId(), draft.localId());
            }
        }
        List<ChunkDraft> remapped = new ArrayList<>();
        for (ChunkDraft draft : drafts) {
            String parentId = draft.parentLocalId();
            if (parentId != null) {
                if (parentRemap.containsKey(parentId)) {
                    parentId = parentRemap.get(parentId);
                }
                if (!parentLocalIds.containsKey(parentId)) {
                    parentId = null;
                }
            }
            if (!Objects.equals(parentId, draft.parentLocalId())) {
                remapped.add(new ChunkDraft(draft.content(), draft.localId(), parentId, draft.level()));
            } else {
                remapped.add(draft);
            }
        }
        return remapped;
    }

    private static boolean hasArticleMarkers(String markdown) {
        return markdown.lines().anyMatch(line -> ARTICLE_LINE_PATTERN.matcher(line.trim()).find());
    }

    private static List<ChunkDraft> assignChildLocalIds(List<ChunkDraft> drafts) {
        List<ChunkDraft> result = new ArrayList<>();
        int childIndex = 0;
        for (ChunkDraft draft : drafts) {
            if (draft.isChild() && draft.localId() == null) {
                result.add(new ChunkDraft(
                        draft.content(),
                        "ch" + childIndex++,
                        draft.parentLocalId(),
                        ChunkLevel.CHILD
                ));
            } else {
                result.add(draft);
            }
        }
        return result;
    }

    private static List<ChunkDraft> expandMicroChunks(List<ChunkDraft> drafts, int microMaxChars) {
        List<ChunkDraft> result = new ArrayList<>();
        int microIndex = 0;
        for (ChunkDraft draft : drafts) {
            if (!draft.isChild()) {
                result.add(draft);
                continue;
            }
            result.add(draft);
            List<String> microParts = mergeHeadingMicroParts(splitMicro(draft.content(), microMaxChars));
            if (microParts.isEmpty()) {
                continue;
            }
            String childLocalId = draft.localId();
            for (String micro : microParts) {
                result.add(new ChunkDraft(
                        micro,
                        "m" + microIndex++,
                        childLocalId,
                        ChunkLevel.MICRO
                ));
            }
        }
        return result;
    }

    private static List<String> splitMicro(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        if (content.length() <= maxChars) {
            return List.of(content.trim());
        }
        List<String> paragraphs = chunkByParagraph(content);
        List<String> micros = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > maxChars) {
                flushMicroBuffer(micros, buffer);
                for (int i = 0; i < paragraph.length(); i += maxChars) {
                    micros.add(paragraph.substring(i, Math.min(i + maxChars, paragraph.length())).trim());
                }
                continue;
            }
            if (buffer.length() + paragraph.length() + 1 > maxChars) {
                flushMicroBuffer(micros, buffer);
            }
            if (!buffer.isEmpty()) {
                buffer.append('\n');
            }
            buffer.append(paragraph);
        }
        flushMicroBuffer(micros, buffer);
        return micros.isEmpty() ? List.of(content.trim()) : micros;
    }

    private static void flushMicroBuffer(List<String> micros, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            micros.add(buffer.toString().trim());
            buffer.setLength(0);
        }
    }

    private static List<String> mergeHeadingMicroParts(List<String> microParts) {
        if (microParts == null || microParts.isEmpty()) {
            return List.of();
        }
        List<String> merged = new ArrayList<>();
        String pendingHeading = null;
        for (String micro : microParts) {
            if (ChunkHeadingHeuristics.looksLikeShortHeading(micro)) {
                if (merged.isEmpty()) {
                    pendingHeading = micro;
                } else {
                    int lastIndex = merged.size() - 1;
                    merged.set(lastIndex, merged.get(lastIndex) + "\n" + micro);
                }
                continue;
            }
            if (pendingHeading != null) {
                merged.add(pendingHeading + "\n" + micro);
                pendingHeading = null;
            } else {
                merged.add(micro);
            }
        }
        return merged;
    }

    private static List<ChunkDraft> chunkByArticles(String markdown) {
        List<ChunkDraft> drafts = new ArrayList<>();
        String currentChapterKey = null;
        int chapterIndex = 0;

        String[] lines = markdown.split("\n");
        StringBuilder articleBuffer = new StringBuilder();
        String currentArticleTitle = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                if (!articleBuffer.isEmpty()) {
                    articleBuffer.append('\n');
                }
                continue;
            }
            if (line.startsWith("第") && line.contains("章") && !ARTICLE_LINE_PATTERN.matcher(line).find()) {
                flushArticle(drafts, currentChapterKey, currentArticleTitle, articleBuffer);
                currentArticleTitle = null;
                currentChapterKey = "c" + chapterIndex++;
                drafts.add(new ChunkDraft(line, currentChapterKey, null, ChunkLevel.PARENT));
                continue;
            }
            if (ARTICLE_LINE_PATTERN.matcher(line).find()) {
                flushArticle(drafts, currentChapterKey, currentArticleTitle, articleBuffer);
                currentArticleTitle = line;
                articleBuffer.setLength(0);
                articleBuffer.append(line);
                continue;
            }
            if (currentArticleTitle != null) {
                articleBuffer.append('\n').append(line);
            } else if (currentChapterKey == null) {
                currentChapterKey = "c" + chapterIndex++;
                drafts.add(new ChunkDraft("", currentChapterKey, null, ChunkLevel.PARENT));
                currentArticleTitle = line;
                articleBuffer.append(line);
            } else {
                articleBuffer.append('\n').append(line);
            }
        }
        flushArticle(drafts, currentChapterKey, currentArticleTitle, articleBuffer);

        if (drafts.isEmpty()) {
            String[] articles = ARTICLE_SPLIT_PATTERN.split(markdown.trim());
            for (String article : articles) {
                String trimmed = article.trim();
                if (!trimmed.isBlank()) {
                    drafts.add(new ChunkDraft(trimmed, null, null, ChunkLevel.CHILD));
                }
            }
        }
        return drafts;
    }

    private static void flushArticle(
            List<ChunkDraft> drafts,
            String currentChapterKey,
            String currentArticleTitle,
            StringBuilder articleBuffer
    ) {
        if (currentArticleTitle == null || articleBuffer.isEmpty()) {
            return;
        }
        String content = articleBuffer.toString().trim();
        if (!content.isBlank()) {
            drafts.add(new ChunkDraft(content, null, currentChapterKey, ChunkLevel.CHILD));
        }
        articleBuffer.setLength(0);
    }

    private static List<ChunkDraft> chunkByHeaders(String markdown) {
        List<ChunkDraft> drafts = new ArrayList<>();
        String[] blocks = markdown.split("\n\\s*\n");
        String currentParentKey = null;
        int parentIndex = 0;

        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (HEADER_PATTERN.matcher(trimmed).matches()) {
                currentParentKey = "p" + parentIndex++;
                drafts.add(new ChunkDraft("", currentParentKey, null, ChunkLevel.PARENT));
            } else if (currentParentKey != null) {
                drafts.add(new ChunkDraft(trimmed, null, currentParentKey, ChunkLevel.CHILD));
            } else {
                drafts.add(new ChunkDraft(trimmed, null, null, ChunkLevel.CHILD));
            }
        }
        return drafts;
    }

    private static List<ChunkDraft> chunkFlatWithVirtualParents(String markdown) {
        List<String> paragraphs = chunkByParagraph(markdown);
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<ChunkDraft> drafts = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i += 5) {
            String parentKey = "p" + (i / 5);
            drafts.add(new ChunkDraft("", parentKey, null, ChunkLevel.PARENT));
            for (int j = i; j < Math.min(i + 5, paragraphs.size()); j++) {
                drafts.add(new ChunkDraft(paragraphs.get(j), null, parentKey, ChunkLevel.CHILD));
            }
        }
        return drafts;
    }
}
