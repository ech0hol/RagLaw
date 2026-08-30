package com.raglaw.rag.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.ContractChunkDto;
import com.raglaw.rag.dto.ContractTextDto;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractTextService {

    private final ContractAccessService contractAccessService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ContractRiskRepository riskRepository;
    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    public ContractTextService(
            ContractAccessService contractAccessService,
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            ContractRiskRepository riskRepository,
            IngestService ingestService,
            ObjectMapper objectMapper
    ) {
        this.contractAccessService = contractAccessService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.riskRepository = riskRepository;
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ContractTextDto getText(String documentId) {
        DocumentEntity document = ensureContract(documentId);
        String filename = ingestService.resolveOriginalFilename(document);
        String lowerFilename = filename.toLowerCase();
        boolean pdf = lowerFilename.endsWith(".pdf");
        boolean image = isImageFilename(lowerFilename);
        String content = buildFullText(documentId);
        List<ContractChunkDto> chunks = listDisplayChunks(documentId);
        ExtractMeta meta = parseExtractMeta(document.getMetadataJson());
        return new ContractTextDto(
                documentId,
                filename,
                content,
                pdf,
                meta.extractMethod,
                meta.ocrUsed,
                image,
                chunks
        );
    }

    private List<ContractChunkDto> listDisplayChunks(String documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).stream()
                .filter(this::isDisplayChunk)
                .map(chunk -> new ContractChunkDto(
                        chunk.getId(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        chunk.getChunkLevel() != null ? chunk.getChunkLevel().name() : null
                ))
                .toList();
    }

    private boolean isDisplayChunk(DocumentChunkEntity chunk) {
        if (chunk.getChunkLevel() == ChunkLevel.PARENT) {
            return false;
        }
        String text = chunk.getContent() == null ? "" : chunk.getContent().trim();
        if (text.isEmpty()) {
            return false;
        }
        return !text.matches("^段落组\\s*\\d+$");
    }

    @Transactional(readOnly = true)
    public String buildRevisedText(String documentId) {
        ensureContract(documentId);
        return buildFullText(documentId);
    }

    String buildFullText(String documentId) {
        List<DocumentChunkEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<ContractRiskEntity> risks = riskRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
        Map<String, List<ContractRiskEntity>> acceptedByChunk = risks.stream()
                .filter(ContractRiskEntity::isAccepted)
                .collect(Collectors.groupingBy(ContractRiskEntity::getChunkId));

        return chunks.stream()
                .map(chunk -> resolveChunkContent(chunk, acceptedByChunk.get(chunk.getId())))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private String resolveChunkContent(DocumentChunkEntity chunk, List<ContractRiskEntity> acceptedRisks) {
        if (acceptedRisks == null || acceptedRisks.isEmpty()) {
            return chunk.getContent();
        }
        String content = chunk.getContent();
        for (ContractRiskEntity risk : acceptedRisks) {
            if (risk.getRevisedExcerpt() != null && !risk.getRevisedExcerpt().isBlank()) {
                content = risk.getRevisedExcerpt();
            } else {
                content = RevisionCalculator.compute(content, risk.getExcerpt(), risk.getSuggestion());
            }
        }
        return content;
    }

    private static boolean isImageFilename(String lowerFilename) {
        return lowerFilename.endsWith(".jpg")
                || lowerFilename.endsWith(".jpeg")
                || lowerFilename.endsWith(".png");
    }

    private DocumentEntity ensureContract(String documentId) {
        return contractAccessService.requireOwnedContract(documentId);
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
