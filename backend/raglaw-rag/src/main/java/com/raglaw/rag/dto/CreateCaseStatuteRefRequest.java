package com.raglaw.rag.dto;

public record CreateCaseStatuteRefRequest(
        String caseDocumentId,
        String statuteDocumentId,
        String refType
) {
}
