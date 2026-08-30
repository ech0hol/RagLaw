package com.raglaw.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentFullTextServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private IngestService ingestService;

    private DocumentFullTextService documentFullTextService;

    @BeforeEach
    void setUp() {
        documentFullTextService = new DocumentFullTextService(
                documentRepository,
                chunkRepository,
                ingestService,
                new ObjectMapper()
        );
    }

    @Test
    void buildFullTextExcludesParentAndVirtualLabels() {
        String documentId = "doc-1";
        DocumentChunkEntity parent = new DocumentChunkEntity(
                "parent-1",
                documentId,
                null,
                ChunkLevel.PARENT,
                0,
                "段落组 1",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/ADMIN",
                null
        );
        DocumentChunkEntity child = new DocumentChunkEntity(
                "child-1",
                documentId,
                "parent-1",
                ChunkLevel.CHILD,
                1,
                "第一条 正文内容",
                "/STATUTE",
                "/STATUTE/CIVIL",
                "/STATUTE/CIVIL/ADMIN",
                null
        );
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId))
                .thenReturn(List.of(parent, child));

        String fullText = documentFullTextService.buildFullText(documentId);

        assertThat(fullText).isEqualTo("第一条 正文内容");
        assertThat(fullText).doesNotContain("段落组");
    }
}
