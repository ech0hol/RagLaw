package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.search.EsSearchResult;
import com.raglaw.rag.service.EmbeddingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class HybridRetrieverTest {

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ElasticsearchRetriever elasticsearchRetriever;

    @Mock
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;

    private HybridRetriever hybridRetriever;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ChannelThresholdFilter channelThresholdFilter = new ChannelThresholdFilter(ragProperties);
        RetrievalSubstantiveFilter retrievalSubstantiveFilter = new RetrievalSubstantiveFilter();
        hybridRetriever = new HybridRetriever(
                documentChunkRepository,
                embeddingService,
                elasticsearchRetrieverProvider,
                new RetrievalReranker(),
                new RetrievalDiversifier(),
                new QueryRewriteService(),
                ragProperties,
                channelThresholdFilter,
                retrievalSubstantiveFilter
        );
    }

    @Test
    void searchUsesFullTextOnlyWhenEsDisabled() {
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(null);
        when(documentChunkRepository.searchFullText(eq("劳动合同"), anyList(), eq(0), eq(20)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "chunk-1", "doc-1", "劳动合同解除条款", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR", 1.5d
                }));

        List<RetrievalHit> hits = hybridRetriever.search("劳动合同", List.of(), 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-1");
        assertThat(hits.get(0).content()).contains("劳动合同");
    }

    @Test
    void searchFusesBm25AndKnnWithRrf() {
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(elasticsearchRetriever);
        when(elasticsearchRetriever.isEnabled()).thenReturn(true);
        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingService.embed("工伤赔偿")).thenReturn(Optional.of(new float[]{0.1f, 0.2f}));

        when(elasticsearchRetriever.searchBm25Result(eq("工伤赔偿"), eq(List.of("/CASE/CIVIL/WORK")), eq(12)))
                .thenReturn(EsSearchResult.success(List.of(
                        new RetrievalHit("chunk-a", "doc-a", "工伤认定流程", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 2.0),
                        new RetrievalHit("chunk-b", "doc-b", "赔偿标准说明", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 1.5)
                )));
        when(elasticsearchRetriever.searchKnnResult(any(float[].class), eq(List.of("/CASE/CIVIL/WORK")), eq(12)))
                .thenReturn(EsSearchResult.success(List.of(
                        new RetrievalHit("chunk-c", "doc-c", "司法解释条文", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 0.9),
                        new RetrievalHit("chunk-a", "doc-a", "工伤认定流程", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 0.8)
                )));

        List<RetrievalHit> hits = hybridRetriever.search("工伤赔偿", List.of("/CASE/CIVIL/WORK"), 3);

        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-a");
        assertThat(hits.stream().map(RetrievalHit::chunkId)).contains("chunk-b", "chunk-c");
    }

    @Test
    void searchScopedToDocumentUsesDocumentQuery() {
        when(documentChunkRepository.searchFullTextForDocument(eq("违约金"), eq("doc-contract-1"), eq(20)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "chunk-1", "doc-contract-1", "违约金条款", "/CONTRACT", "/CONTRACT/CIVIL", "/CONTRACT/CIVIL/GENERAL", 2.0d
                }));

        List<RetrievalHit> hits = hybridRetriever.search("违约金", List.of(), 5, null, "doc-contract-1");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).documentId()).isEqualTo("doc-contract-1");
    }

    @Test
    void retriesWithArticleOrientedQueryWhenFirstPassOnlyReturnsToc() {
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(null);
        when(documentChunkRepository.searchFullText(
                org.mockito.ArgumentMatchers.anyString(),
                anyList(),
                anyInt(),
                anyInt()
        )).thenReturn(List.<Object[]>of(new Object[]{
                        "toc-1", "doc-1", """
                        第一编 总则
                        第三章 刑罚
                        第一节 刑罚的种类
                        """, "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 2.0d
                }))
                .thenReturn(List.<Object[]>of(
                new Object[]{
                        "toc-1", "doc-1", """
                        第一编 总则
                        第三章 刑罚
                        """, "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 2.0d
                },
                new Object[]{
                        "art-33", "doc-1", "第三十三条 刑罚分为主刑和附加刑。",
                        "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 1.5d
                }
        ));

        List<RetrievalHit> hits = hybridRetriever.search("刑法中有哪些刑罚", List.of(), 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("art-33");
        assertThat(hits.get(0).content()).contains("第三十三条");
    }

    @Test
    void searchAdditionalPunishmentPrefersArticle33Or34() {
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(null);
        when(documentChunkRepository.searchFullText(
                org.mockito.ArgumentMatchers.anyString(),
                anyList(),
                anyInt(),
                anyInt()
        )).thenReturn(List.<Object[]>of(new Object[]{
                        "toc-1", "doc-1", """
                        第一编 总则
                        第三章 刑罚
                        第一节 刑罚的种类
                        """, "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 2.0d
                }))
                .thenReturn(List.<Object[]>of(
                new Object[]{
                        "toc-1", "doc-1", """
                        第一编 总则
                        第三章 刑罚
                        """, "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 2.0d
                },
                new Object[]{
                        "art-33", "doc-1", "第三十三条 刑罚分为主刑和附加刑。",
                        "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 1.5d
                },
                new Object[]{
                        "art-34", "doc-1", "第三十四条 附加刑的种类如下：罚金、剥夺政治权利、没收财产。",
                        "/STATUTE", "/STATUTE/CRIMINAL", "/STATUTE/CRIMINAL/GENERAL", 1.4d
                }
        ));

        List<RetrievalHit> hits = hybridRetriever.search("附加刑的种类", List.of(), 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.stream().anyMatch(hit ->
                hit.content().contains("第三十三条") || hit.content().contains("第三十四条")
        )).isTrue();
    }

    @Test
    void rrfFusionPrefersItemsPresentInBothLists() {
        RetrievalHit a = new RetrievalHit("a", "d1", "content-a", "/l1", "/l2", "/l3", 0);
        RetrievalHit b = new RetrievalHit("b", "d2", "content-b", "/l1", "/l2", "/l3", 0);
        RetrievalHit c = new RetrievalHit("c", "d3", "content-c", "/l1", "/l2", "/l3", 0);

        List<RetrievalHit> fused = RrfFusion.fuse(
                List.of(List.of(a, b), List.of(c, a)),
                RrfFusion.DEFAULT_K,
                3
        );

        assertThat(fused.get(0).chunkId()).isEqualTo("a");
        assertThat(fused).extracting(RetrievalHit::chunkId).containsExactlyInAnyOrder("a", "b", "c");
    }
}
