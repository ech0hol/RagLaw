package com.raglaw.rag.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.ContractSummaryDto;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.DocumentDeletionService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractDocumentService {

    private final ContractAccessService contractAccessService;
    private final DocumentRepository documentRepository;
    private final ContractRiskRepository riskRepository;
    private final DocumentDeletionService documentDeletionService;
    private final ObjectMapper objectMapper;

    public ContractDocumentService(
            ContractAccessService contractAccessService,
            DocumentRepository documentRepository,
            ContractRiskRepository riskRepository,
            DocumentDeletionService documentDeletionService,
            ObjectMapper objectMapper
    ) {
        this.contractAccessService = contractAccessService;
        this.documentRepository = documentRepository;
        this.riskRepository = riskRepository;
        this.documentDeletionService = documentDeletionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ContractSummaryDto> listForCurrentUser() {
        String userId = contractAccessService.requireUserId();
        return documentRepository.findByDocTypeAndUploaderIdOrderByCreatedAtDesc("CONTRACT", userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public void deleteForCurrentUser(String documentId) {
        contractAccessService.requireOwnedContract(documentId);
        documentDeletionService.deleteDocument(documentId);
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
            return "CONTRACT";
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
        return "CONTRACT";
    }
}
