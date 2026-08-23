package com.raglaw.rag.dto;

import java.util.List;

public record ContractReviewDto(
        String documentId,
        String suggestedAgentCode,
        String extractMethod,
        boolean ocrUsed,
        List<ContractRiskDto> risks
) {
}
