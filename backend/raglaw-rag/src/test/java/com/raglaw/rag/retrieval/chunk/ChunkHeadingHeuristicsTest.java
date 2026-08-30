package com.raglaw.rag.retrieval.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChunkHeadingHeuristicsTest {

    @Test
    void detectsTableOfContentsWithMultipleChapterLines() {
        String toc = """
                第一编 总则
                第二章 犯罪
                第三章 刑罚
                第一节 刑罚的种类
                第二节 管制
                """;

        assertThat(ChunkHeadingHeuristics.looksLikeTableOfContents(toc)).isTrue();
    }

    @Test
    void doesNotTreatSubstantiveArticleAsTableOfContents() {
        String article = "第三十三条 刑罚分为主刑和附加刑。";

        assertThat(ChunkHeadingHeuristics.looksLikeTableOfContents(article)).isFalse();
        assertThat(ChunkHeadingHeuristics.containsSubstantiveArticleBody(article)).isTrue();
    }

    @Test
    void detectsExplicitCatalogHeading() {
        assertThat(ChunkHeadingHeuristics.looksLikeTableOfContents("目录\n第一章 总则")).isTrue();
    }

    @Test
    void extractsMultipleArticleAnchors() {
        assertThat(ChunkHeadingHeuristics.extractArticleAnchors("第32条、第33条、第34条"))
                .containsExactly("第32条", "第33条", "第34条");
    }
}
