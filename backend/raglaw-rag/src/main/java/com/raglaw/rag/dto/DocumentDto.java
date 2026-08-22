package com.raglaw.rag.dto;

import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import java.time.Instant;

public record DocumentDto(
        String id,
        String categoryId,
        String title,
        String docType,
        DocStatus status,
        String uploaderId,
        String minioKey,
        String rejectReason,
        Instant createdAt,
        Instant updatedAt
) {

    public static DocumentDto from(DocumentEntity entity) {
        return new DocumentDto(
                entity.getId(),
                entity.getCategoryId(),
                entity.getTitle(),
                entity.getDocType(),
                entity.getStatus(),
                entity.getUploaderId(),
                entity.getMinioKey(),
                entity.getRejectReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
