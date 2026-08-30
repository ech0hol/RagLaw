package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.retrieval.funnel.FunnelResult;
import com.raglaw.rag.tool.HybridRagSearchTool;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractRagContextBuilderTest {

    @Mock
    private HybridRagSearchTool hybridRagSearchTool;

    private ContractRagContextBuilder builder;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        properties.getContract().setRagHitLimit(5);
        builder = new ContractRagContextBuilder(
                hybridRagSearchTool,
                new ContractKnowledgeScopeMapper(),
                properties
        );
    }

    @Test
    void build_formatsHitsIntoPrompt() {
        DocumentEntity document = new DocumentEntity("doc-1", "cat", "劳动合同", "CONTRACT", "user", "key.md");
        RagSearchHit hit = new RagSearchHit(
                "chunk-1",
                "doc-1",
                1.0,
                "/STATUTE/SOCIAL/LABOR",
                "试用期不得超过六个月",
                "试用期不得超过六个月",
                "劳动合同法"
        );
        when(hybridRagSearchTool.searchDetailed(
                any(),
                any(),
                eq(5),
                eq("CONTRACT"),
                eq("doc-1"),
                eq(false),
                eq("contract_review")
        )).thenReturn(new RagSearchResult(
                List.of(hit),
                new FunnelResult(List.of(), List.of(), List.of(), 0, 0, "SCOPE", "test", false, List.of()),
                Map.of()
        ));

        ContractRagContextBuilder.ContractRagContext context = builder.build(
                document,
                "CONTRACT",
                "双方约定试用期六个月"
        );

        assertThat(context.hitCount()).isEqualTo(1);
        assertThat(context.promptBlock()).contains("劳动合同法");
        assertThat(context.promptBlock()).contains("STATUTE");
        assertThat(context.references()).hasSize(1);
        assertThat(context.references().get(0).docType()).isEqualTo("STATUTE");
    }

    @Test
    void build_emptyHits_notesNoKnowledge() {
        DocumentEntity document = new DocumentEntity("doc-2", "cat", "合同", "CONTRACT", "user", "key.md");
        when(hybridRagSearchTool.searchDetailed(any(), any(), any(Integer.class), any(), eq("doc-2"), any(Boolean.class), any()))
                .thenReturn(new RagSearchResult(
                        List.of(),
                        new FunnelResult(List.of(), List.of(), List.of(), 0, 0, "SCOPE", "test", false, List.of()),
                        Map.of()
                ));

        ContractRagContextBuilder.ContractRagContext context = builder.build(document, "CONTRACT", "正文");
        assertThat(context.hitCount()).isZero();
        assertThat(context.promptBlock()).contains("未检索到");
    }
}
