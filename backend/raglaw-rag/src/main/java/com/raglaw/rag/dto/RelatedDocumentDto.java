package com.raglaw.rag.dto;

public record RelatedDocumentDto(
        String documentId,
        String title,
        String docType,
        String refType
) {
}
