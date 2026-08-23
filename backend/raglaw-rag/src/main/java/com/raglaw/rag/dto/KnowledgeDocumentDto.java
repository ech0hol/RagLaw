package com.raglaw.rag.dto;

import java.util.List;

public record KnowledgeDocumentDto(
        DocumentDto document,
        List<KnowledgeChunkDto> chunks,
        List<RelatedDocumentDto> relatedDocuments
) {
}
