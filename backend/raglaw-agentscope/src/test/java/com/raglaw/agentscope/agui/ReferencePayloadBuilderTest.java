package com.raglaw.agentscope.agui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.CitationRelevanceFilter;
import com.raglaw.rag.retrieval.ReferenceExcerptEnhancer;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferencePayloadBuilderTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ReferenceExcerptEnhancer referenceExcerptEnhancer;

    private ReferencePayloadBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ReferencePayloadBuilder(
                documentRepository,
                referenceExcerptEnhancer,
                new CitationRelevanceFilter(new RagProperties()),
                new ObjectMapper()
        );
    }

    @Test
    void buildPayloadsMatchesSerializeJson() throws Exception {
        RagSearchHit hit = new RagSearchHit(
                "chunk-1",
                "doc-1",
                0.9,
                "/STATUTE/CIVIL",
                "excerpt text",
                "llm content",
                null
        );
        DocumentEntity document = org.mockito.Mockito.mock(DocumentEntity.class);
        when(document.getTitle()).thenReturn("劳动合同法");
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));
        when(referenceExcerptEnhancer.enhance(eq("excerpt text"), eq("doc-1"), any())).thenReturn("excerpt text");

        List<Map<String, Object>> payloads = builder.buildPayloads(List.of(hit));
        String json = builder.serializeJson(List.of(hit));

        assertEquals(1, payloads.size());
        Map<String, Object> payload = payloads.get(0);
        assertEquals(1, payload.get("index"));
        assertEquals("chunk-1", payload.get("chunkId"));
        assertEquals("doc-1", payload.get("documentId"));
        assertEquals("/STATUTE/CIVIL", payload.get("path"));
        assertEquals("excerpt text", payload.get("excerpt"));
        assertEquals("llm content", payload.get("content"));
        assertEquals(0.9, payload.get("score"));
        assertEquals("劳动合同法", payload.get("title"));
        assertEquals("knowledge", payload.get("source"));
        assertEquals(new ObjectMapper().readTree(json), new ObjectMapper().valueToTree(payloads));
    }

    @Test
    void buildPayloadUsesEnhancedExcerptForUserQuery() {
        RagSearchHit hit = new RagSearchHit(
                "chunk-1",
                "doc-1",
                0.9,
                "/STATUTE/CIVIL",
                "第一章 总则",
                "llm content",
                null
        );
        when(referenceExcerptEnhancer.enhance("第一章 总则", "doc-1", "买化学品违法吗"))
                .thenReturn("第十七条 办理购用证明或备案。");
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.empty());

        Map<String, Object> payload = builder.buildPayload(hit, 1, "买化学品违法吗");

        assertEquals("第十七条 办理购用证明或备案。", payload.get("excerpt"));
    }

    @Test
    void buildPayloadIncludesArticleLabelFromExcerpt() {
        RagSearchHit hit = new RagSearchHit(
                "chunk-1",
                "doc-1",
                0.9,
                "/STATUTE/CIVIL",
                "第六条 国家推广普通话。",
                "llm content",
                null
        );
        when(referenceExcerptEnhancer.enhance("第六条 国家推广普通话。", "doc-1", null))
                .thenReturn("第六条 国家推广普通话。");
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.empty());

        Map<String, Object> payload = builder.buildPayload(hit, 1, null);

        assertEquals("第六条", payload.get("articleLabel"));
    }

    @Test
    void serializeJsonReturnsNullForEmptyHits() {
        assertNull(builder.serializeJson(List.of()));
        assertNull(builder.serializeJson(null));
    }

    @Test
    void buildPayloadSkipsEnhancerForCatalogHits() {
        RagSearchHit hit = new RagSearchHit(
                "catalog-doc-1",
                "doc-1",
                1.0,
                "/STATUTE/CIVIL",
                "类型: 法规 | 分类: /STATUTE/CIVIL",
                "类型: 法规 | 分类: /STATUTE/CIVIL",
                "民法典",
                "catalog-doc-1"
        );
        DocumentEntity document = org.mockito.Mockito.mock(DocumentEntity.class);
        when(document.getTitle()).thenReturn("民法典");
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));

        Map<String, Object> payload = builder.buildPayload(hit, 2, "当前有哪些法规可以查询");

        assertEquals("类型: 法规 | 分类: /STATUTE/CIVIL", payload.get("excerpt"));
        verify(referenceExcerptEnhancer, never()).enhance(any(), any(), any());
    }

    @Test
    void citableHitsAlignsWithUserVisiblePayloadIndices() {
        RagSearchHit summary = new RagSearchHit(
                "catalog-summary", null, 1.0, "/KNOWLEDGE", "概览", "概览", "概览", "catalog-summary");
        RagSearchHit doc = new RagSearchHit(
                "chunk-1", "doc-1", 0.9, "/STATUTE", "条文", "条文", "民法典", null);
        List<RagSearchHit> citable = ReferencePayloadBuilder.citableHits(List.of(summary, doc));
        assertEquals(1, citable.size());
        assertEquals("chunk-1", citable.get(0).chunkId());
        assertEquals(1, ReferencePayloadBuilder.countCitableKnowledgeHits(List.of(summary, doc)));
    }

    @Test
    void buildUserVisiblePayloadsFiltersCatalogSummaryAndRenumbers() {
        RagSearchHit summary = new RagSearchHit(
                "catalog-summary",
                null,
                1.0,
                "/KNOWLEDGE",
                "知识库概览",
                "知识库概览",
                "知识库概览",
                "catalog-summary"
        );
        RagSearchHit doc = new RagSearchHit(
                "catalog-doc-1",
                "doc-1",
                1.0,
                "/STATUTE/CIVIL",
                "类型: 法规 | 分类: /STATUTE/CIVIL",
                "类型: 法规 | 分类: /STATUTE/CIVIL",
                "民法典",
                "catalog-doc-1"
        );
        DocumentEntity document = org.mockito.Mockito.mock(DocumentEntity.class);
        when(document.getTitle()).thenReturn("民法典");
        when(documentRepository.findById("doc-1")).thenReturn(java.util.Optional.of(document));

        List<Map<String, Object>> payloads = builder.buildUserVisiblePayloads(List.of(summary, doc), "目录");

        assertEquals(1, payloads.size());
        assertEquals(1, payloads.get(0).get("index"));
        assertEquals("catalog-doc-1", payloads.get(0).get("chunkId"));
    }

    @Test
    void buildWebPayloadIncludesWebSource() {
        WebReference webRef = new WebReference(
                2,
                "web-call-0",
                "示例网页",
                "https://example.com/article",
                "网页摘要内容"
        );

        Map<String, Object> payload = builder.buildWebPayload(webRef);

        assertEquals(2, payload.get("index"));
        assertEquals("web-call-0", payload.get("chunkId"));
        assertEquals("https://example.com/article", payload.get("path"));
        assertEquals("示例网页", payload.get("title"));
        assertEquals("web", payload.get("source"));
        assertEquals("网页摘要内容", payload.get("excerpt"));
    }

    @Test
    void relevantCitableHitsFiltersConstitutionalNoiseForMedicalQuery() {
        List<RagSearchHit> hits = List.of(
                new RagSearchHit("c1", "d1", 8.0, "/STATUTE/SOCIAL/GENERAL", "医保", "医保", "社会救助法"),
                new RagSearchHit("c2", "d2", 7.0, "/STATUTE/CONSTITUTIONAL/GENERAL", "居民委员会", "居民委员会", "宪法"),
                new RagSearchHit("c3", "d3", 6.0, "/STATUTE/SOCIAL/GENERAL", "社会保险", "社会保险", "社会保险法")
        );

        List<RagSearchHit> filtered = builder.relevantCitableHits(hits, "城乡居民医保可以异地报销吗");

        assertEquals(2, filtered.size());
        assertEquals("社会救助法", filtered.get(0).title());
        assertEquals("社会保险法", filtered.get(1).title());
    }
}
