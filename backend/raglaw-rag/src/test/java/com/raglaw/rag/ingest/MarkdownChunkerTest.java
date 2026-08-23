package com.raglaw.rag.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {

    @Test
    void statuteMarkdown_createsParentChildChunks() {
        String md = "## 第一章\n\n第一段。\n\n第二段。\n\n## 第二章\n\n第三段。";
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy(md);
        long parents = chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).count();
        long children = chunks.stream().filter(MarkdownChunker.ChunkDraft::isChild).count();
        assertTrue(parents >= 2);
        assertTrue(children >= 3);
    }

    @Test
    void flatMarkdown_createsVirtualParents() {
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy("a\n\nb\n\nc");
        assertEquals(4, chunks.size());
        assertEquals(1, chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).count());
        assertEquals(3, chunks.stream().filter(MarkdownChunker.ChunkDraft::isChild).count());
    }
}
