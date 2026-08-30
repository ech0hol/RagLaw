package com.raglaw.rag.ingest;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertTrue(chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).allMatch(chunk ->
                chunk.content().isEmpty() || chunk.content().contains("章")));
    }

    @Test
    void flatMarkdown_createsVirtualParentsAndMicroPerChild() {
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy("a\n\nb\n\nc");
        assertEquals(7, chunks.size());
        assertEquals(1, chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).count());
        assertEquals(3, chunks.stream().filter(MarkdownChunker.ChunkDraft::isChild).count());
        assertEquals(3, chunks.stream().filter(MarkdownChunker.ChunkDraft::isMicro).count());
        assertTrue(chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).allMatch(chunk ->
                chunk.content().isEmpty() || chunk.content().contains("章")));
    }

    @Test
    void shortChildChunk_generatesAtLeastOneMicro() {
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy("## 标题\n\n短段落。");
        assertTrue(chunks.stream().anyMatch(MarkdownChunker.ChunkDraft::isMicro));
    }

    @Test
    void statuteArticles_createParentChildChunks() {
        String md = """
                第一章 总则

                第一条 为了加强危险化学品安全管理，制定本条例。

                第二条 危险化学品安全管理，适用本条例。
                """;
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy(md);
        long parents = chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).count();
        long children = chunks.stream().filter(MarkdownChunker.ChunkDraft::isChild).count();
        assertTrue(parents >= 1);
        assertTrue(children >= 2);
        assertTrue(chunks.stream().filter(MarkdownChunker.ChunkDraft::isParent).allMatch(chunk ->
                chunk.content().isEmpty() || chunk.content().contains("章")));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().startsWith("第一条")));
    }

    @Test
    void chapterHeadingMergedIntoFollowingMicro() {
        String md = """
                ## 第一章 总则

                第一条 为了加强危险化学品安全管理，制定本条例。
                """;
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy(md);
        List<MarkdownChunker.ChunkDraft> micros = chunks.stream()
                .filter(MarkdownChunker.ChunkDraft::isMicro)
                .toList();
        assertThat(micros).hasSize(1);
        assertThat(micros.get(0).content()).contains("第一条");
        assertThat(micros.stream().noneMatch(micro -> micro.content().equals("第一章 总则"))).isTrue();
    }

    @Test
    void tocChapterList_doesNotCreateDuplicateChapterParents() {
        String md = """
                第一章 总则
                第二章 生产、储存安全
                第三章 使用安全

                第一章 总则

                第一条 为了加强危险化学品安全管理，制定本条例。

                第二条 危险化学品安全管理，适用本条例。
                """;
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy(md);
        long chapterParents = chunks.stream()
                .filter(MarkdownChunker.ChunkDraft::isParent)
                .filter(chunk -> chunk.content().contains("第一章"))
                .count();
        assertThat(chapterParents).isEqualTo(1);
        assertThat(chunks.stream().anyMatch(chunk -> chunk.content().startsWith("第一条"))).isTrue();
        assertThat(chunks.stream().filter(MarkdownChunker.ChunkDraft::isMicro)
                .noneMatch(micro -> micro.content().trim().equals("第一章 总则"))).isTrue();
    }

    @Test
    void spacedArticleLines_useArticleChunking() {
        String md = """
                第一章 总则

                第 七 条 对危险化学品的生产、储存、使用、经营、运输实施安全监督管理。
                """;
        List<MarkdownChunker.ChunkDraft> chunks = MarkdownChunker.chunkHierarchy(md);
        assertThat(chunks.stream().anyMatch(chunk -> chunk.content().contains("七") && chunk.content().contains("条"))).isTrue();
    }
}