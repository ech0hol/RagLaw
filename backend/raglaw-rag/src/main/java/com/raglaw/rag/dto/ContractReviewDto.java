package com.raglaw.rag.dto;

import java.util.List;

public record ContractReviewDto(
        String documentId,
        String suggestedAgentCode,
        String extractMethod,
        boolean ocrUsed,
        String analysisModel,
        int ragHitCount,
        String reviewStatus,
        String reviewError,
        String ingestStage,
        List<ContractRiskDto> risks
) {
}
