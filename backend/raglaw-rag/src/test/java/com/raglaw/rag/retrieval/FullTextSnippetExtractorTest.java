package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import java.util.List;

class FullTextSnippetExtractorTest {

    @Test
    void extractSurroundsNgramMatchedTerm() {
        String fullText = "前言。本法规范危险化学品生产与储存。";
        String snippet = FullTextSnippetExtractor.extract(fullText, "化学 危险化学品", 80);
        assertThat(snippet).contains("危险化学品");
    }

    @Test
    void extractSurroundsMatchedTerm() {
        String fullText = "前言内容。第十七条 办理购用证明或备案。第九十九条 违反本法规定。";
        String snippet = FullTextSnippetExtractor.extract(fullText, "购用证明", 80);
        assertThat(snippet).contains("购用证明");
        assertThat(snippet).contains("第十七条");
    }

    @Test
    void extractTruncatesWhenNoMatch() {
        String fullText = "abcdefghijklmnopqrstuvwxyz";
        String snippet = FullTextSnippetExtractor.extract(fullText, "化学", 10);
        assertThat(snippet).hasSizeLessThanOrEqualTo(11);
    }

    @Test
    void extractSkippingHeadingsSkipsChapterTitleMatch() {
        String fullText = "第一章 总则\n第七条 对危险化学品的生产、储存、使用、经营、运输实施安全监督管理。";
        String snippet = FullTextSnippetExtractor.extractSkippingHeadings(fullText, "第一章", 120);
        assertThat(snippet).contains("第七条");
        assertThat(snippet).doesNotContain("第一章 总则");
    }

    @Test
    void extractAroundAnchorFindsChapterBody() {
        String fullText = "前言。\n第九章 法律责任\n第九十九条 违反本法规定，构成犯罪的。";
        String snippet = FullTextSnippetExtractor.extractAroundAnchor(fullText, "第九章", 120);
        assertThat(snippet).contains("第九十九条");
    }

    @Test
    void extractAroundArticlesJoinsMultipleAnchors() {
        String fullText = "第三十二条 刑罚分为主刑和附加刑。\n第三十三条 主刑包括管制。";
        String snippet = FullTextSnippetExtractor.extractAroundArticles(
                fullText,
                List.of("第32条", "第33条"),
                320
        );
        assertThat(snippet).contains("第三十二条");
        assertThat(snippet).contains("第三十三条");
    }

    @Test
    void extractAroundAnchorResolvesArabicArticleNumber() {
        String fullText = "第三十二条 刑罚分为主刑和附加刑。";
        String snippet = FullTextSnippetExtractor.extractAroundAnchor(fullText, "第32条", 120);
        assertThat(snippet).contains("第三十二条");
    }
}
