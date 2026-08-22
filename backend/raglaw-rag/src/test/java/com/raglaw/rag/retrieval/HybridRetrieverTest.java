package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.service.EmbeddingService;
import com.raglaw.rag.service.VectorStoreService;
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
    private VectorStoreService vectorStoreService;

    @Mock
    private ObjectProvider<VectorStoreService> vectorStoreProvider;

    private HybridRetriever hybridRetriever;

    @BeforeEach
    void setUp() {
        hybridRetriever = new HybridRetriever(documentChunkRepository, embeddingService, vectorStoreProvider);
    }

    @Test
    void searchUsesFullTextOnlyWhenPgDisabled() {
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        when(documentChunkRepository.searchFullText(eq("劳动合同"), anyList(), eq(0), eq(5)))
                .thenReturn(List.<Object[]>of(new Object[]{
                        "chunk-1", "doc-1", "劳动合同解除条款", "/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR", 1.5d
                }));

        List<RetrievalHit> hits = hybridRetriever.search("劳动合同", List.of(), 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-1");
        assertThat(hits.get(0).content()).contains("劳动合同");
    }

    @Test
    void searchFusesFullTextAndVectorWithRrf() {
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStoreService);
        when(vectorStoreService.isEnabled()).thenReturn(true);
        when(embeddingService.isEnabled()).thenReturn(true);
        when(embeddingService.embed("工伤赔偿")).thenReturn(Optional.of(new float[]{0.1f, 0.2f}));

        when(documentChunkRepository.searchFullText(eq("工伤赔偿"), anyList(), eq(1), eq(3)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{"chunk-a", "doc-a", "工伤认定流程", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 2.0d},
                        new Object[]{"chunk-b", "doc-b", "赔偿标准说明", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 1.5d}
                ));

        when(vectorStoreService.search(any(float[].class), eq(List.of("/CASE/CIVIL/WORK")), eq(3)))
                .thenReturn(List.of(
                        new VectorStoreService.VectorHit("chunk-c", "doc-c", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 0.9),
                        new VectorStoreService.VectorHit("chunk-a", "doc-a", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", 0.8)
                ));

        DocumentChunkEntity chunkA = new DocumentChunkEntity(
                "chunk-a", "doc-a", null, 0, "工伤认定流程", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", null
        );
        DocumentChunkEntity chunkC = new DocumentChunkEntity(
                "chunk-c", "doc-c", null, 0, "司法解释条文", "/CASE", "/CASE/CIVIL", "/CASE/CIVIL/WORK", null
        );
        when(documentChunkRepository.findAllById(List.of("chunk-c", "chunk-a")))
                .thenReturn(List.of(chunkC, chunkA));

        List<RetrievalHit> hits = hybridRetriever.search("工伤赔偿", List.of("/CASE/CIVIL/WORK"), 3);

        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).chunkId()).isEqualTo("chunk-a");
        assertThat(hits.stream().map(RetrievalHit::chunkId)).contains("chunk-b", "chunk-c");
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
