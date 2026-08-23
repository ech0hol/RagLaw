package com.raglaw.rag.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MarkdownChunker {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^#{2,3}\\s+.+");

    private MarkdownChunker() {
    }

    public record ChunkDraft(String content, String localId, String parentLocalId) {

        public boolean isParent() {
            return localId != null && parentLocalId == null;
        }

        public boolean isChild() {
            return parentLocalId != null;
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
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String normalized = markdown.replace("\r\n", "\n");
        boolean hasHeaders = normalized.lines().anyMatch(line -> HEADER_PATTERN.matcher(line.trim()).matches());
        if (hasHeaders) {
            return chunkByHeaders(normalized);
        }
        return chunkFlatWithVirtualParents(normalized);
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
                drafts.add(new ChunkDraft(trimmed, currentParentKey, null));
            } else if (currentParentKey != null) {
                drafts.add(new ChunkDraft(trimmed, null, currentParentKey));
            } else {
                drafts.add(new ChunkDraft(trimmed, null, null));
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
            drafts.add(new ChunkDraft("段落组 " + ((i / 5) + 1), parentKey, null));
            for (int j = i; j < Math.min(i + 5, paragraphs.size()); j++) {
                drafts.add(new ChunkDraft(paragraphs.get(j), null, parentKey));
            }
        }
        return drafts;
    }
}
