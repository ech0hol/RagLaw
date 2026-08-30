package com.raglaw.rag.dto;

public record CategoryDocumentCountDto(
        String categoryId,
        String path,
        long count
) {
}
