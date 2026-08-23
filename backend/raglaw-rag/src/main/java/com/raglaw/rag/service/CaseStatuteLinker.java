package com.raglaw.rag.service;

import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.CaseStatuteRefEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.CaseStatuteRefRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseStatuteLinker {

    private static final int MAX_LINKS = 5;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final CaseStatuteRefRepository caseStatuteRefRepository;

    public CaseStatuteLinker(
            DocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            CaseStatuteRefRepository caseStatuteRefRepository
    ) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.caseStatuteRefRepository = caseStatuteRefRepository;
    }

    @Transactional
    public void linkCaseToStatutes(String caseDocumentId) {
        DocumentEntity caseDoc = documentRepository.findById(caseDocumentId).orElse(null);
        if (caseDoc == null || !"CASE".equals(caseDoc.getDocType())) {
            return;
        }
        List<DocumentEntity> statutes = documentRepository.findByDocTypeAndStatus("STATUTE", DocStatus.INDEXED);
        if (statutes.isEmpty()) {
            return;
        }
        String caseText = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(caseDocumentId).stream()
                .map(DocumentChunkEntity::getContent)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (caseText.isBlank()) {
            return;
        }
        Set<String> linked = new HashSet<>();
        for (DocumentEntity statute : statutes) {
            if (linked.size() >= MAX_LINKS) {
                break;
            }
            String title = statute.getTitle();
            if (title == null || title.isBlank() || title.length() < 4) {
                continue;
            }
            if (caseText.contains(title) && linked.add(statute.getId())) {
                caseStatuteRefRepository.save(new CaseStatuteRefEntity(
                        Ids.newId(),
                        caseDocumentId,
                        statute.getId(),
                        "AUTO_TITLE"
                ));
            }
        }
    }
}
