package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceExcerptEnhancerTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private com.raglaw.rag.service.DocumentFullTextService documentFullTextService;

    private ReferenceExcerptEnhancer enhancer;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        enhancer = new ReferenceExcerptEnhancer(
                documentRepository,
                documentFullTextService,
                new QueryRewriteService(),
                properties
        );
    }

    @Test
    void enhancesHeadingExcerptFromFullText() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "危险化学品安全法", "STATUTE", "user", "key.md");
        document.setFullText("第一章 总则。第十七条 办理购用证明或备案。");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        String enhanced = enhancer.enhance("第一章 总则", "doc-1", "买化学品");

        assertThat(enhanced).contains("购用证明");
    }

    @Test
    void fallsBackToChunkFullTextWhenDocumentFullTextEmpty() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "危险化学品安全法", "STATUTE", "user", "key.md");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));
        when(documentFullTextService.buildFullText("doc-1"))
                .thenReturn("第一章 总则。第十七条 办理购用证明或备案。");

        String enhanced = enhancer.enhance("第一章 总则", "doc-1", "买化学品");

        assertThat(enhanced).contains("购用证明");
    }

    @Test
    void enhancesUsingArticleAnchorBeforeUserQuery() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "语言文字法", "STATUTE", "user", "key.md");
        document.setFullText("第六条 国家推广普通话。");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        String enhanced = enhancer.enhance("第六条", "doc-1", "无关检索词");

        assertThat(enhanced).contains("第六条");
        assertThat(enhanced).contains("国家推广");
    }

    @Test
    void enhancesUsingChapterAnchor() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "危险化学品安全法", "STATUTE", "user", "key.md");
        document.setFullText("第九章 法律责任。第九十九条 违反本法规定，构成犯罪的，依法追究刑事责任。");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        String enhanced = enhancer.enhance("第九章 法律责任", "doc-1", "无关问题");

        assertThat(enhanced).contains("第九十九条");
    }

    @Test
    void keepsSubstantiveExcerpt() {
        String excerpt = "第十七条 办理购用证明或备案。";
        assertThat(enhancer.enhance(excerpt, "doc-1", "买化学品")).isEqualTo(excerpt);
    }

    @Test
    void enhancesTableOfContentsExcerptUsingUserQuery() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "中华人民共和国刑法", "STATUTE", "user", "key.md");
        document.setFullText("""
                第一编 总则
                第三章 刑罚
                第一节 刑罚的种类
                第三十三条 刑罚分为主刑和附加刑。
                第三十四条 附加刑的种类如下：
                （一）罚金；
                """);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        String tocExcerpt = """
                第一编 总则
                第三章 刑罚
                第一节 刑罚的种类
                """;
        String enhanced = enhancer.enhance(tocExcerpt, "doc-1", "刑法中有哪些刑罚");

        assertThat(enhanced).contains("第三十三条");
        assertThat(enhanced).contains("主刑和附加刑");
        assertThat(ChunkHeadingHeuristics.looksLikeTableOfContents(enhanced)).isFalse();
    }

    @Test
    void returnsEmptyForUnenhanceableTableOfContents() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "刑法", "STATUTE", "user", "key.md");
        document.setFullText("第一编 总则\n第三章 刑罚");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        String enhanced = enhancer.enhance(
                """
                第一编 总则
                第三章 刑罚
                第一节 刑罚的种类
                """,
                "doc-1",
                "无关问题"
        );

        assertThat(enhanced).isEmpty();
    }
}
