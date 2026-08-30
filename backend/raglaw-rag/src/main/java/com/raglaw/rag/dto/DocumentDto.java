package com.raglaw.rag.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import java.time.Instant;

public record DocumentDto(
        String id,
        String categoryId,
        String title,
        String originalFilename,
        String docType,
        DocStatus status,
        String uploaderId,
        String minioKey,
        String rejectReason,
        String ingestStage,
        String ingestError,
        Instant createdAt,
        Instant updatedAt,
        String effectiveDate
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DocumentDto from(DocumentEntity entity) {
        return from(entity, resolveOriginalFilename(entity));
    }

    public static DocumentDto from(DocumentEntity entity, String originalFilename) {
        return new DocumentDto(
                entity.getId(),
                entity.getCategoryId(),
                entity.getTitle(),
                originalFilename,
                entity.getDocType(),
                entity.getStatus(),
                entity.getUploaderId(),
                entity.getMinioKey(),
                entity.getRejectReason(),
                entity.getIngestStage(),
                entity.getIngestError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                parseEffectiveDate(entity.getMetadataJson())
        );
    }

    private static String parseEffectiveDate(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(metadataJson);
            String value = root.path("effectiveDate").asText("");
            return value.isBlank() ? null : value;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String resolveOriginalFilename(DocumentEntity document) {
        String key = document.getMinioKey();
        if (key == null || key.isBlank()) {
            return document.getTitle() + ".md";
        }
        int slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
