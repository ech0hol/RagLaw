package com.raglaw.rag.dto;

import com.raglaw.rag.domain.DocStatus;
import java.time.Instant;

public record ContractSummaryDto(
        String documentId,
        String title,
        DocStatus status,
        Instant createdAt,
        Instant updatedAt,
        int riskCount,
        String suggestedAgentCode
) {
}
