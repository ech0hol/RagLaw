package com.raglaw.rag.service;

import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentDto;
import com.raglaw.rag.dto.KnowledgeChunkDto;
import com.raglaw.rag.dto.KnowledgeDocumentDto;
import com.raglaw.rag.dto.RelatedDocumentDto;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeDocumentService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            KnowledgeGraphService knowledgeGraphService
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    public KnowledgeDocumentDto getDocument(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        var chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .map(this::toChunkDto)
                .toList();
        List<RelatedDocumentDto> relatedDocuments = knowledgeGraphService.listRelatedDocuments(documentId);
        return new KnowledgeDocumentDto(DocumentDto.from(document), chunks, relatedDocuments);
    }

    private KnowledgeChunkDto toChunkDto(DocumentChunkEntity chunk) {
        return new KnowledgeChunkDto(
                chunk.getId(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getL1Path(),
                chunk.getL2Path(),
                chunk.getL3Path(),
                chunk.getChunkLevel() != null ? chunk.getChunkLevel().name() : null
        );
    }
}
