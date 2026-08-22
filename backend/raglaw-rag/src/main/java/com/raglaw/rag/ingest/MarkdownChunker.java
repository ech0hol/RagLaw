package com.raglaw.rag.ingest;

import java.util.ArrayList;
import java.util.List;

public final class MarkdownChunker {

    private MarkdownChunker() {
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
}
