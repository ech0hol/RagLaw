package com.raglaw.rag.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.ContractRiskEntity;
import java.time.Instant;
import java.util.List;

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
        List<HighlightRect> highlightRects,
        boolean accepted,
        String revisedExcerpt,
        List<LegalReferenceDto> legalReferences,
        Instant createdAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
                parseHighlightRects(entity.getHighlightRectsJson()),
                entity.isAccepted(),
                entity.getRevisedExcerpt(),
                parseLegalReferences(entity.getLegalReferencesJson()),
                entity.getCreatedAt()
        );
    }

    private static List<HighlightRect> parseHighlightRects(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<HighlightRect>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static List<LegalReferenceDto> parseLegalReferences(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<LegalReferenceDto>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
