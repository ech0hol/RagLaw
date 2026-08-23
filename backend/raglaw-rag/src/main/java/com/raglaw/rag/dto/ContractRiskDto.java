package com.raglaw.rag.dto;

import com.raglaw.rag.domain.ContractRiskEntity;
import java.time.Instant;

public record ContractRiskDto(
        String id,
        String documentId,
        String chunkId,
        String severity,
        String dimension,
        String summary,
        String excerpt,
        String suggestion,
        Integer pageNumber,
        boolean accepted,
        Instant createdAt
) {

    public static ContractRiskDto from(ContractRiskEntity entity) {
        return new ContractRiskDto(
                entity.getId(),
                entity.getDocumentId(),
                entity.getChunkId(),
                entity.getSeverity(),
                entity.getDimension(),
                entity.getSummary(),
                entity.getExcerpt(),
                entity.getSuggestion(),
                entity.getPageNumber(),
                entity.isAccepted(),
                entity.getCreatedAt()
        );
    }
}
