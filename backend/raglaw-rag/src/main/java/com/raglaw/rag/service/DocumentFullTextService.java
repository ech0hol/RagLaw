package com.raglaw.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentTextDto;
import com.raglaw.rag.ingest.DisplayChunkFilter;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentFullTextService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    public DocumentFullTextService(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            IngestService ingestService,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DocumentTextDto getText(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        String filename = ingestService.resolveOriginalFilename(document);
        String content = buildFullText(documentId);
        ExtractMeta meta = parseExtractMeta(document.getMetadataJson());
        return new DocumentTextDto(
                documentId,
                filename,
                content,
                meta.extractMethod,
                meta.ocrUsed
        );
    }

    public String buildFullText(String documentId) {
        List<DocumentChunkEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        return chunks.stream()
                .filter(DisplayChunkFilter::includeInFullText)
                .map(DocumentChunkEntity::getContent)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private ExtractMeta parseExtractMeta(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new ExtractMeta(null, false);
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            String method = root.has("extractMethod") ? root.get("extractMethod").asText() : null;
            boolean ocrUsed = root.path("ocrUsed").asBoolean(false);
            return new ExtractMeta(method, ocrUsed);
        } catch (Exception ex) {
            return new ExtractMeta(null, false);
        }
    }

    private record ExtractMeta(String extractMethod, boolean ocrUsed) {
    }
}
