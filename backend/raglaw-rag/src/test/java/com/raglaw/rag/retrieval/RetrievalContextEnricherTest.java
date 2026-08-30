package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.chunk.ChunkHierarchyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetrievalContextEnricherTest {

    @Mock
    private ChunkHierarchyResolver chunkHierarchyResolver;

    @Mock
    private DocumentRepository documentRepository;

    private RetrievalContextEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new RetrievalContextEnricher(
                chunkHierarchyResolver,
                documentRepository,
                new RagProperties()
        );
    }

    @Test
    void fallsBackToHitContentWhenResolvedExcerptIsChapterHeading() {
        String article = "第七条 对危险化学品的生产、储存、使用、经营、运输实施安全监督管理。";
        when(chunkHierarchyResolver.resolve("micro-1"))
                .thenReturn(new ChunkHierarchyResolver.ResolvedChunk(
                        "micro-1",
                        "child-1",
                        "第一章 总则",
                        "第一章 总则\n" + article
                ));

        var hit = enricher.toSearchHit(new RetrievalHit(
                "micro-1",
                "doc-1",
                article,
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/LABOR",
                57.6
        ));

        assertThat(hit.excerpt()).contains("第七条");
        assertThat(hit.excerpt()).isNotEqualTo("第一章 总则");
    }
}
