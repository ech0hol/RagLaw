package com.raglaw.rag.contract;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.dto.ContractRiskDto;
import com.raglaw.rag.dto.ContractTextDto;
import com.raglaw.rag.dto.LegalReferenceDto;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractChatContextBuilder {

    private static final int DEFAULT_MAX_TEXT_CHARS = 12_000;
    private static final int MAX_RISK_FIELD_CHARS = 500;

    private final ContractAccessService contractAccessService;
    private final ContractTextService contractTextService;
    private final ContractReviewService contractReviewService;
    private final RagProperties ragProperties;

    public ContractChatContextBuilder(
            ContractAccessService contractAccessService,
            ContractTextService contractTextService,
            ContractReviewService contractReviewService,
            RagProperties ragProperties
    ) {
        this.contractAccessService = contractAccessService;
        this.contractTextService = contractTextService;
        this.contractReviewService = contractReviewService;
        this.ragProperties = ragProperties;
    }

    @Transactional(readOnly = true)
    public ContractChatContext enrichUserMessage(String documentId, String userMessage) {
        if (documentId == null || documentId.isBlank() || userMessage == null) {
            return ContractChatContext.passthrough(userMessage);
        }
        Optional<String> contextBlock = buildContextBlock(documentId);
        if (contextBlock.isEmpty()) {
            return ContractChatContext.passthrough(userMessage);
        }
        String block = contextBlock.get();
        int textChars = extractTextChars(documentId);
        int riskCount = contractReviewService.listRisks(documentId).size();
        String enriched = block + "\n\n【用户问题】\n" + userMessage.trim();
        return new ContractChatContext(enriched, true, textChars, riskCount);
    }

    @Transactional(readOnly = true)
    public Optional<String> buildContextBlock(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return Optional.empty();
        }
        try {
            contractAccessService.requireOwnedContract(documentId);
        } catch (Exception ex) {
            return Optional.empty();
        }
        ContractTextDto text = contractTextService.getText(documentId);
        List<ContractRiskDto> risks = contractReviewService.listRisks(documentId);
        return Optional.of(formatContext(text.filename(), text.content(), risks));
    }

    private int extractTextChars(String documentId) {
        try {
            return contractTextService.getText(documentId).content().length();
        } catch (Exception ex) {
            return 0;
        }
    }

    private String formatContext(String filename, String content, List<ContractRiskDto> risks) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前合同】\n");
        sb.append("标题/文件名：").append(blankToDash(filename)).append('\n');
        sb.append("正文：\n");
        sb.append(truncateText(content)).append('\n');
        sb.append('\n');
        sb.append(formatRisks(risks));
        sb.append('\n');
        sb.append("【说明】以上为本系统已持久化的合同内容与审查结果，请在此基础上回答用户问题，勿要求用户重复提供合同全文。");
        return sb.toString();
    }

    private String formatRisks(List<ContractRiskDto> risks) {
        int maxRisks = Math.max(1, ragProperties.getContract().getMaxRisksPerDocument());
        StringBuilder sb = new StringBuilder();
        if (risks.isEmpty()) {
            sb.append("【已识别审查意见】暂无已持久化审查意见");
            return sb.toString();
        }
        int limit = Math.min(risks.size(), maxRisks);
        sb.append("【已识别审查意见】（共 ").append(risks.size()).append(" 条");
        if (risks.size() > limit) {
            sb.append("，以下展示前 ").append(limit).append(" 条");
        }
        sb.append("）\n");
        for (int i = 0; i < limit; i++) {
            ContractRiskDto risk = risks.get(i);
            sb.append(i + 1).append(". [")
                    .append(blankToDash(risk.severity())).append('/')
                    .append(blankToDash(risk.dimension())).append("] ")
                    .append(truncateField(risk.summary())).append('\n');
            if (risk.excerpt() != null && !risk.excerpt().isBlank()) {
                sb.append("   原文摘录：").append(truncateField(risk.excerpt())).append('\n');
            }
            if (risk.suggestion() != null && !risk.suggestion().isBlank()) {
                sb.append("   修订建议：").append(truncateField(risk.suggestion())).append('\n');
            }
            String legalRefs = formatLegalReferences(risk.legalReferences());
            if (!legalRefs.isBlank()) {
                sb.append("   法律依据：").append(legalRefs).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private String formatLegalReferences(List<LegalReferenceDto> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        return references.stream()
                .map(ref -> truncateField(ref.title()))
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + "；" + b)
                .orElse("");
    }

    private String truncateText(String content) {
        if (content == null || content.isBlank()) {
            return "（暂无正文）";
        }
        int maxChars = ragProperties.getContract().getChatContextMaxChars();
        if (maxChars <= 0) {
            maxChars = DEFAULT_MAX_TEXT_CHARS;
        }
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "\n…（正文已截断）";
    }

    private static String truncateField(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= MAX_RISK_FIELD_CHARS) {
            return value.trim();
        }
        return value.substring(0, MAX_RISK_FIELD_CHARS).trim() + "…";
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
