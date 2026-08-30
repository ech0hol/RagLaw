package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.KnowledgeHitDto;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.FullTextScoreFilter;
import com.raglaw.rag.retrieval.QueryRewriteService;
import com.raglaw.rag.retrieval.funnel.FunnelDegradationPolicy;
import com.raglaw.rag.retrieval.funnel.FunnelResult;
import com.raglaw.rag.retrieval.funnel.KnowledgeFunnelRouter;
import com.raglaw.rag.search.ElasticsearchRetriever;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class DocumentKnowledgeSearchServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private KnowledgeScopeResolver knowledgeScopeResolver;
    @Mock
    private KnowledgeFunnelRouter knowledgeFunnelRouter;
    @Mock
    private QueryRewriteService queryRewriteService;
    @Mock
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;
    @Mock
    private CategoryService categoryService;

    private DocumentKnowledgeSearchService service;
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getRetrieval().setFunnelEnabled(false);
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(null);
        service = new DocumentKnowledgeSearchService(
                documentRepository,
                knowledgeScopeResolver,
                knowledgeFunnelRouter,
                queryRewriteService,
                new FullTextScoreFilter(ragProperties),
                ragProperties,
                elasticsearchRetrieverProvider,
                categoryService
        );
    }

    @Test
    void searchReturnsOneHitPerDocumentWithSnippet() {
        when(knowledgeScopeResolver.resolvePaths(List.of("STATUTE"))).thenReturn(List.of("/STATUTE"));
        when(queryRewriteService.rewrite("化学")).thenReturn("化学 危险化学品");
        when(documentRepository.searchFullTextDocuments(
                anyString(),
                anyList(),
                anyInt(),
                anyList(),
                eq(0),
                anyInt()
        )).thenReturn(List.<Object[]>of(new Object[] {"doc-1", "/STATUTE/CIVIL/LABOR", 2.5}));

        DocumentEntity document = new DocumentEntity("doc-1", "cat", "危险化学品安全法", "STATUTE", "user", "key.md");
        document.setFullText("第一章 总则。本法所称危险化学品相关活动须依法办理。");
        when(documentRepository.findAllById(List.of("doc-1"))).thenReturn(List.of(document));

        List<KnowledgeHitDto> hits = service.search("化学", List.of("STATUTE"), "STATUTE", null, 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).documentId()).isEqualTo("doc-1");
        assertThat(hits.get(0).chunkId()).isEqualTo("doc-1");
        assertThat(hits.get(0).excerpt()).contains("危险化学品");
    }

    @Test
    void searchDedupesSameTitleDocuments() {
        when(knowledgeScopeResolver.resolvePaths(List.of("STATUTE"))).thenReturn(List.of("/STATUTE"));
        when(queryRewriteService.rewrite("化学")).thenReturn("化学 危险化学品");
        when(documentRepository.searchFullTextDocuments(
                anyString(),
                anyList(),
                anyInt(),
                anyList(),
                eq(0),
                anyInt()
        )).thenReturn(List.<Object[]>of(
                new Object[] {"doc-1", "/STATUTE/CIVIL/LABOR", 2.5},
                new Object[] {"doc-2", "/STATUTE/CIVIL/LABOR", 1.0}
        ));

        DocumentEntity doc1 = new DocumentEntity("doc-1", "cat", "危险化学品安全法_20251227", "STATUTE", "user", "key1.md");
        doc1.setFullText("危险化学品储存规定。");
        DocumentEntity doc2 = new DocumentEntity("doc-2", "cat", "危险化学品安全法_20251227", "STATUTE", "user", "key2.md");
        doc2.setFullText("另一份危险化学品副本。");
        when(documentRepository.findAllById(List.of("doc-1", "doc-2"))).thenReturn(List.of(doc1, doc2));

        List<KnowledgeHitDto> hits = service.search("化学", List.of("STATUTE"), "STATUTE", null, 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    void searchUsesFunnelWhenEnabled() {
        ragProperties.getRetrieval().setFunnelEnabled(true);
        ragProperties.getKnowledgeSearch().setUseFunnel(true);
        when(knowledgeScopeResolver.resolvePaths(List.of("STATUTE"))).thenReturn(List.of("/STATUTE"));
        when(knowledgeFunnelRouter.route(anyString(), anyList(), isNull(), eq(false), anyString()))
                .thenReturn(new FunnelDegradationPolicy.AppliedFunnel(
                        new FunnelResult(List.of("/STATUTE"), List.of(), List.of("doc-1"), 0.9, 0.9, "DOCUMENT", "", false, List.of()),
                        List.of("/STATUTE"),
                        List.of(),
                        List.of("doc-1")
                ));
        when(queryRewriteService.rewrite("化学")).thenReturn("化学 危险化学品");
        when(documentRepository.searchFullTextDocuments(
                anyString(),
                anyList(),
                anyInt(),
                anyList(),
                eq(0),
                anyInt()
        )).thenReturn(List.<Object[]>of(new Object[] {"doc-1", "/STATUTE/CIVIL/LABOR", 2.5}));

        DocumentEntity document = new DocumentEntity("doc-1", "cat", "危险化学品安全法", "STATUTE", "user", "key.md");
        document.setFullText("危险化学品相关条文。");
        when(documentRepository.findAllById(List.of("doc-1"))).thenReturn(List.of(document));

        List<KnowledgeHitDto> hits = service.search("化学", List.of("STATUTE"), "STATUTE", null, 10);

        assertThat(hits).hasSize(1);
    }
}
