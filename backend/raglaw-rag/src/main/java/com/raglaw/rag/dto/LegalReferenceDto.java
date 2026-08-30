package com.raglaw.rag.dto;

public record LegalReferenceDto(
        String documentId,
        String title,
        String excerpt,
        String docType
) {
}
