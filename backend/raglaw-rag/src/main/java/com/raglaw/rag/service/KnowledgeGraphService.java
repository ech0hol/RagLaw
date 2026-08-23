package com.raglaw.rag.service;

import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.CaseStatuteRefEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.CreateCaseStatuteRefRequest;
import com.raglaw.rag.dto.RelatedDocumentDto;
import com.raglaw.rag.repository.CaseStatuteRefRepository;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGraphService {

    private final CaseStatuteRefRepository caseStatuteRefRepository;
    private final DocumentRepository documentRepository;

    public KnowledgeGraphService(
            CaseStatuteRefRepository caseStatuteRefRepository,
            DocumentRepository documentRepository
    ) {
        this.caseStatuteRefRepository = caseStatuteRefRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public List<RelatedDocumentDto> listRelatedDocuments(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        List<CaseStatuteRefEntity> refs = "CASE".equals(document.getDocType())
                ? caseStatuteRefRepository.findByCaseDocId(documentId)
                : caseStatuteRefRepository.findByStatuteId(documentId);

        if (refs.isEmpty()) {
            return List.of();
        }

        List<String> relatedIds = refs.stream()
                .map(ref -> "CASE".equals(document.getDocType()) ? ref.getStatuteId() : ref.getCaseDocId())
                .distinct()
                .toList();
        Map<String, DocumentEntity> documents = documentRepository.findAllById(relatedIds).stream()
                .collect(Collectors.toMap(DocumentEntity::getId, Function.identity()));

        List<RelatedDocumentDto> related = new ArrayList<>();
        for (CaseStatuteRefEntity ref : refs) {
            String relatedId = "CASE".equals(document.getDocType()) ? ref.getStatuteId() : ref.getCaseDocId();
            DocumentEntity relatedDoc = documents.get(relatedId);
            if (relatedDoc != null) {
                related.add(new RelatedDocumentDto(
                        relatedDoc.getId(),
                        relatedDoc.getTitle(),
                        relatedDoc.getDocType(),
                        ref.getRefType()
                ));
            }
        }
        return related;
    }

    @Transactional
    public RelatedDocumentDto createRef(CreateCaseStatuteRefRequest request) {
        DocumentEntity caseDoc = documentRepository.findById(request.caseDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Case document not found"));
        DocumentEntity statuteDoc = documentRepository.findById(request.statuteDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Statute document not found"));
        if (!"CASE".equals(caseDoc.getDocType())) {
            throw new IllegalArgumentException("caseDocumentId must reference a CASE document");
        }
        if (!"STATUTE".equals(statuteDoc.getDocType())) {
            throw new IllegalArgumentException("statuteDocumentId must reference a STATUTE document");
        }
        String refType = request.refType() == null || request.refType().isBlank() ? "APPLY" : request.refType();
        CaseStatuteRefEntity saved = caseStatuteRefRepository.save(new CaseStatuteRefEntity(
                Ids.newId(),
                caseDoc.getId(),
                statuteDoc.getId(),
                refType
        ));
        return new RelatedDocumentDto(statuteDoc.getId(), statuteDoc.getTitle(), statuteDoc.getDocType(), saved.getRefType());
    }
}
