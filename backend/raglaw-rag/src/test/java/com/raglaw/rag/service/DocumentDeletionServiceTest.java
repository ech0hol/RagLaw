package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentDeletionServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ContractRiskRepository riskRepository;
    @Mock
    private DocumentChunkRepository chunkRepository;
    @Mock
    private DocumentStorageService documentStorageService;
    @Mock
    private IndexOutboxService indexOutboxService;

    private DocumentDeletionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentDeletionService(
                documentRepository,
                riskRepository,
                chunkRepository,
                documentStorageService,
                indexOutboxService
        );
    }

    @Test
    void deleteDocumentRemovesChunksStorageAndRow() {
        DocumentEntity document = new DocumentEntity(
                "doc-1", "cat-1", "劳动法节选", "STATUTE", "user-1", "uploads/doc-1.md"
        );
        document.setStatus(DocStatus.INDEXED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(document));

        service.deleteDocument("doc-1");

        verify(indexOutboxService).enqueueDeleteDocument("doc-1", document.getIndexVersion());
        verify(indexOutboxService).flushPendingForDocument("doc-1");
        verify(chunkRepository).deleteByDocumentId("doc-1");
        verify(documentStorageService).delete("uploads/doc-1.md");
        verify(documentRepository).delete(document);
    }

    @Test
    void deleteAllByDocTypesRejectsContract() {
        assertThrows(BusinessException.class, () -> service.deleteAllByDocTypes(List.of("CONTRACT")));
    }

    @Test
    void deleteAllByDocTypesDeletesKnowledgeDocuments() {
        DocumentEntity statute = new DocumentEntity(
                "doc-s", "cat-1", "法规", "STATUTE", "user-1", "key-s"
        );
        DocumentEntity caseDoc = new DocumentEntity(
                "doc-c", "cat-2", "案例", "CASE", "user-1", "key-c"
        );
        when(documentRepository.findByDocType("STATUTE")).thenReturn(List.of(statute));
        when(documentRepository.findByDocType("CASE")).thenReturn(List.of(caseDoc));

        int deleted = service.deleteAllByDocTypes(List.of("STATUTE", "CASE"));

        assertEquals(2, deleted);
        verify(indexOutboxService).enqueueDeleteDocument("doc-s", statute.getIndexVersion());
        verify(indexOutboxService).enqueueDeleteDocument("doc-c", caseDoc.getIndexVersion());
        verify(chunkRepository).deleteByDocumentId("doc-s");
        verify(chunkRepository).deleteByDocumentId("doc-c");
        verify(documentRepository).delete(statute);
        verify(documentRepository).delete(caseDoc);
    }
}
