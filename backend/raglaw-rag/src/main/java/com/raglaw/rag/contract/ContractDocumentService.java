package com.raglaw.rag.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.ContractSummaryDto;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.VectorStoreService;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractDocumentService {

    private final DocumentRepository documentRepository;
    private final ContractRiskRepository riskRepository;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentStorageService documentStorageService;
    private final ObjectProvider<VectorStoreService> vectorStoreService;
    private final ObjectMapper objectMapper;

    public ContractDocumentService(
            DocumentRepository documentRepository,
            ContractRiskRepository riskRepository,
            DocumentChunkRepository chunkRepository,
            DocumentStorageService documentStorageService,
            ObjectProvider<VectorStoreService> vectorStoreService,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.riskRepository = riskRepository;
        this.chunkRepository = chunkRepository;
        this.documentStorageService = documentStorageService;
        this.vectorStoreService = vectorStoreService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ContractSummaryDto> listForCurrentUser() {
        String userId = requireUserId();
        return documentRepository.findByDocTypeAndUploaderIdOrderByCreatedAtDesc("CONTRACT", userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void deleteForCurrentUser(String documentId) {
        String userId = requireUserId();
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "合同不存在"));
        if (!"CONTRACT".equals(document.getDocType())) {
            throw new BusinessException(ErrorCodes.VALIDATION, "文档不是合同类型");
        }
        if (!userId.equals(document.getUploaderId())) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "无权删除该合同");
        }
        riskRepository.deleteByDocumentId(documentId);
        chunkRepository.deleteByDocumentId(documentId);
        VectorStoreService vectorStore = vectorStoreService.getIfAvailable();
        if (vectorStore != null && vectorStore.isEnabled()) {
            vectorStore.deleteByDocumentId(documentId);
        }
        if (document.getMinioKey() != null && !document.getMinioKey().isBlank()) {
            documentStorageService.delete(document.getMinioKey());
        }
        documentRepository.delete(document);
    }

    private ContractSummaryDto toSummary(DocumentEntity document) {
        int riskCount = riskRepository.findByDocumentIdOrderByCreatedAtAsc(document.getId()).size();
        return new ContractSummaryDto(
                document.getId(),
                document.getTitle(),
                document.getStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                riskCount,
                resolveSuggestedAgentCode(document)
        );
    }

    private String resolveSuggestedAgentCode(DocumentEntity document) {
        String metadata = document.getMetadataJson();
        if (metadata == null || metadata.isBlank()) {
            return "CONTRACT_GENERAL";
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode agent = node.get("suggestedAgentCode");
            if (agent != null && !agent.asText().isBlank()) {
                return agent.asText();
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "CONTRACT_GENERAL";
    }

    private static String requireUserId() {
        String userId = CurrentUserHolder.get();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
