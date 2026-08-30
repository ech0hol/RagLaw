package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.ContractRiskDto;
import com.raglaw.rag.dto.ContractTextDto;
import com.raglaw.rag.dto.LegalReferenceDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractChatContextBuilderTest {

    @Mock
    private ContractAccessService contractAccessService;

    @Mock
    private ContractTextService contractTextService;

    @Mock
    private ContractReviewService contractReviewService;

    private ContractChatContextBuilder builder;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.getContract().setChatContextMaxChars(100);
        ragProperties.getContract().setMaxRisksPerDocument(30);
        builder = new ContractChatContextBuilder(
                contractAccessService,
                contractTextService,
                contractReviewService,
                ragProperties
        );
    }

    @Test
    void enrichUserMessage_includesContractTextAndRisks() {
        DocumentEntity document = contractDocument("doc-1", "租赁合同.pdf");
        when(contractAccessService.requireOwnedContract("doc-1")).thenReturn(document);
        when(contractTextService.getText("doc-1")).thenReturn(new ContractTextDto(
                "doc-1", "租赁合同.pdf", "甲方与乙方约定租赁事项。", false, null, false, false, List.of()
        ));
        when(contractReviewService.listRisks("doc-1")).thenReturn(List.of(sampleRisk()));

        ContractChatContext context = builder.enrichUserMessage("doc-1", "主要风险有哪些？");

        assertThat(context.injected()).isTrue();
        assertThat(context.riskCount()).isEqualTo(1);
        assertThat(context.message()).contains("【当前合同】");
        assertThat(context.message()).contains("甲方与乙方约定租赁事项");
        assertThat(context.message()).contains("【已识别审查意见】");
        assertThat(context.message()).contains("押金过高");
        assertThat(context.message()).contains("【用户问题】");
        assertThat(context.message()).contains("主要风险有哪些？");
    }

    @Test
    void enrichUserMessage_withoutRisks_stillIncludesContractText() {
        DocumentEntity document = contractDocument("doc-2", "服务合同.docx");
        when(contractAccessService.requireOwnedContract("doc-2")).thenReturn(document);
        when(contractTextService.getText("doc-2")).thenReturn(new ContractTextDto(
                "doc-2", "服务合同.docx", "服务内容如下。", false, null, false, false, List.of()
        ));
        when(contractReviewService.listRisks("doc-2")).thenReturn(List.of());

        ContractChatContext context = builder.enrichUserMessage("doc-2", "付款条款如何？");

        assertThat(context.injected()).isTrue();
        assertThat(context.message()).contains("暂无已持久化审查意见");
        assertThat(context.message()).contains("服务内容如下");
    }

    @Test
    void enrichUserMessage_nonContractDocument_passthrough() {
        when(contractAccessService.requireOwnedContract("doc-3"))
                .thenThrow(new com.raglaw.common.exception.BusinessException(
                        com.raglaw.common.api.ErrorCodes.VALIDATION, "仅支持合同文档"));

        ContractChatContext context = builder.enrichUserMessage("doc-3", "解释一下");

        assertThat(context.injected()).isFalse();
        assertThat(context.message()).isEqualTo("解释一下");
    }

    @Test
    void buildContextBlock_truncatesLongContractText() {
        DocumentEntity document = contractDocument("doc-4", "长合同.pdf");
        String longText = "条".repeat(200);
        when(contractAccessService.requireOwnedContract("doc-4")).thenReturn(document);
        when(contractTextService.getText("doc-4")).thenReturn(new ContractTextDto(
                "doc-4", "长合同.pdf", longText, false, null, false, false, List.of()
        ));
        when(contractReviewService.listRisks("doc-4")).thenReturn(List.of());

        Optional<String> block = builder.buildContextBlock("doc-4");

        assertThat(block).isPresent();
        assertThat(block.get()).contains("（正文已截断）");
        assertThat(block.get()).doesNotContain("条".repeat(200));
    }

    private static DocumentEntity contractDocument(String id, String title) {
        return new DocumentEntity(id, "cat-1", title, "CONTRACT", "user-1", "uploads/" + id + ".pdf");
    }

    private static ContractRiskDto sampleRisk() {
        return new ContractRiskDto(
                "risk-1",
                "doc-1",
                "chunk-1",
                "HIGH",
                "条款",
                "押金过高",
                "乙方应支付押金五万元",
                "建议降低押金金额",
                1,
                List.of(),
                false,
                null,
                List.of(new LegalReferenceDto("law-1", "民法典", "押金条款", "STATUTE")),
                Instant.parse("2026-01-01T00:00:00Z")
        );
    }
}
