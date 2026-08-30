package com.raglaw.rag.dto;

import java.util.List;

public record DocumentReindexBatchResultDto(
        int total,
        int succeeded,
        int failed,
        List<DocumentReindexFailureDto> failures
) {
    public record DocumentReindexFailureDto(String documentId, String message) {
    }
}
