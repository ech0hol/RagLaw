package com.raglaw.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.repository.CategoryRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.retrieval.funnel.FunnelDegradationPolicy;
import com.raglaw.rag.retrieval.funnel.FunnelResult;
import com.raglaw.rag.retrieval.funnel.KnowledgeFunnelRouter;
import com.raglaw.rag.service.EmbeddingService;
import com.raglaw.rag.service.KnowledgeCatalogService;
import com.raglaw.rag.service.KnowledgeScopeResolver;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.tool.HybridRagSearchTool;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class RecallBenchmarkExecutionTest {

    private static final List<String> GENERAL_SCOPES = List.of("STATUTE", "CASE");

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ElasticsearchRetriever elasticsearchRetriever;

    @Mock
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;

    @Mock
    private KnowledgeScopeResolver knowledgeScopeResolver;

    @Mock
    private KnowledgeFunnelRouter knowledgeFunnelRouter;

    @Mock
    private RetrievalContextEnricher retrievalContextEnricher;

    private HybridRagSearchTool ragSearchTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ChannelThresholdFilter channelThresholdFilter = new ChannelThresholdFilter(ragProperties);
        RetrievalSubstantiveFilter retrievalSubstantiveFilter = new RetrievalSubstantiveFilter();
        HybridRetriever hybridRetriever = new HybridRetriever(
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
        RetrievalContextEnricher enricher = retrievalContextEnricher;
        ragSearchTool = new HybridRagSearchTool(
                hybridRetriever,
                knowledgeScopeResolver,
                enricher,
                knowledgeFunnelRouter,
                ragProperties,
                new KnowledgeCatalogService(documentRepository, categoryRepository)
        );

        when(knowledgeScopeResolver.resolvePaths(GENERAL_SCOPES)).thenReturn(List.of());
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(null);
        FunnelResult funnelResult = new FunnelResult(
                List.of(),
                List.of(),
                List.of(),
                0.8,
                0.8,
                "SCOPE",
                "",
                false,
                List.of()
        );
        when(knowledgeFunnelRouter.route(any(), anyList(), any(), anyBoolean(), any()))
                .thenReturn(new FunnelDegradationPolicy.AppliedFunnel(
                        funnelResult,
                        List.of(),
                        List.of(),
                        List.of()
                ));
    }

    @Test
    void benchmarkQueriesMeetMinHitCountViaFunnelPath() throws Exception {
        stubLaborCorpusHits();

        try (InputStream input = getClass().getResourceAsStream("/recall-benchmark.json")) {
            JsonNode queries = objectMapper.readTree(input).path("queries");
            for (JsonNode query : queries) {
                String text = query.path("text").asText();
                int minHitCount = query.path("minHitCount").asInt(1);
                String top1PathPrefix = query.path("top1PathPrefix").asText("");

                RagSearchResult result = ragSearchTool.searchDetailed(
                        text,
                        GENERAL_SCOPES,
                        5,
                        "GENERAL",
                        null,
                        false,
                        ""
                );

                assertThat(result.funnelResult())
                        .as("funnel result for query: %s", text)
                        .isNotNull();
                assertThat(result.hits().size())
                        .as("hits for query: %s", text)
                        .isGreaterThanOrEqualTo(minHitCount);

                if (!top1PathPrefix.isBlank()) {
                    RagSearchHit top = result.hits().get(0);
                    String path = top.l1L2L3Path() != null ? top.l1L2L3Path() : "";
                    assertThat(path)
                            .as("top1 path for query: %s", text)
                            .startsWith(top1PathPrefix);
                }
            }
        }
    }

    private void stubLaborCorpusHits() {
        when(documentChunkRepository.searchFullText(any(), anyList(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0);
                    if (query.contains("案例") || query.contains("劳动争议")) {
                        return List.<Object[]>of(row(
                                "case-chunk-1",
                                "doc-case",
                                "劳动争议加班费案例叙述",
                                "/CASE",
                                "/CASE/CIVIL",
                                "/CASE/CIVIL/LABOR",
                                2.0d
                        ));
                    }
                    return List.<Object[]>of(
                            row(
                                    "stat-chunk-1",
                                    "doc-stat",
                                    "劳动合同法 拖欠工资 经济补偿 违约金 加班费",
                                    "/STATUTE",
                                    "/STATUTE/CIVIL",
                                    "/STATUTE/CIVIL/LABOR",
                                    2.5d
                            ),
                            row(
                                    "stat-chunk-2",
                                    "doc-stat-2",
                                    "民法典 定金 租赁合同 借贷利息",
                                    "/STATUTE",
                                    "/STATUTE/CIVIL",
                                    "/STATUTE/CIVIL/CONTRACT",
                                    1.8d
                            ),
                            row(
                                    "chem-chunk-1",
                                    "doc-chem",
                                    "危险化学品购买 行政处罚",
                                    "/STATUTE",
                                    "/STATUTE/ADMIN",
                                    "/STATUTE/ADMIN/SAFETY",
                                    1.6d
                            )
                    );
                });

        when(retrievalContextEnricher.toSearchHit(any())).thenAnswer(invocation -> {
            com.raglaw.rag.dto.RetrievalHit hit = invocation.getArgument(0);
            String path = hit.l3Path() != null && !hit.l3Path().isBlank() ? hit.l3Path() : hit.l2Path();
            return new RagSearchHit(
                    hit.chunkId(),
                    hit.documentId(),
                    hit.score(),
                    path,
                    hit.content(),
                    hit.content(),
                    "fixture"
            );
        });
    }

    private static Object[] row(
            String chunkId,
            String documentId,
            String content,
            String l1,
            String l2,
            String l3,
            double score
    ) {
        return new Object[]{chunkId, documentId, content, l1, l2, l3, score};
    }
}
