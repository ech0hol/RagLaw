package com.raglaw.rag.dto;

public record ContractTextDto(
        String documentId,
        String filename,
        String content,
        boolean pdf
) {
}
