package com.raglaw.rag.dto;

public record DocumentTextDto(
        String documentId,
        String filename,
        String content,
        String extractMethod,
        boolean ocrUsed
) {
}
