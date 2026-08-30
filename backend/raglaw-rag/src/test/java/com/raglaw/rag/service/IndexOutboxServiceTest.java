package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.IndexOutboxEntity;
import com.raglaw.rag.domain.IndexOutboxOperation;
import com.raglaw.rag.domain.IndexOutboxStatus;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.repository.IndexOutboxRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class IndexOutboxServiceTest {

    @Mock
    private IndexOutboxRepository indexOutboxRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;
    @Mock
    private ElasticsearchRetriever elasticsearchRetriever;

    private IndexOutboxService service;
    private RagProperties ragProperties;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getOutbox().setMaxAttempts(3);
        service = new IndexOutboxService(
                indexOutboxRepository,
                documentRepository,
                elasticsearchRetrieverProvider,
                ragProperties,
                new ObjectMapper()
        );
        when(elasticsearchRetrieverProvider.getIfAvailable()).thenReturn(elasticsearchRetriever);
        when(elasticsearchRetriever.isEnabled()).thenReturn(true);
    }

    @Test
    void flushPendingMarksUpsertDone() throws Exception {
        IndexOutboxEntity entry = new IndexOutboxEntity(
                "out-1",
                "doc-1",
                null,
                IndexOutboxOperation.UPSERT,
                "{\"docType\":\"STATUTE\",\"chunks\":[]}",
                2L
        );
        when(indexOutboxRepository.findByDocumentIdAndStatusOrderByCreatedAtAsc("doc-1", IndexOutboxStatus.PENDING))
                .thenReturn(List.of(entry));
        when(elasticsearchRetriever.getDocumentIndexVersion("doc-1")).thenReturn(1L);

        service.flushPendingForDocument("doc-1");

        verify(elasticsearchRetriever).deleteByDocumentIdOrThrow("doc-1");
        verify(elasticsearchRetriever).bulkIndexDocumentOrThrow(eq("doc-1"), eq("STATUTE"), any(), eq(2L));
        verify(indexOutboxRepository).save(entry);
        assertEquals(IndexOutboxStatus.DONE, entry.getStatus());
    }

    @Test
    void flushPendingSkipsStaleVersion() throws Exception {
        IndexOutboxEntity entry = new IndexOutboxEntity(
                "out-1",
                "doc-1",
                null,
                IndexOutboxOperation.UPSERT,
                "{\"docType\":\"STATUTE\",\"chunks\":[]}",
                1L
        );
        when(indexOutboxRepository.findByDocumentIdAndStatusOrderByCreatedAtAsc("doc-1", IndexOutboxStatus.PENDING))
                .thenReturn(List.of(entry));
        when(elasticsearchRetriever.getDocumentIndexVersion("doc-1")).thenReturn(3L);

        service.flushPendingForDocument("doc-1");

        verify(elasticsearchRetriever, never()).bulkIndexDocumentOrThrow(anyString(), anyString(), any(), anyLong());
        assertEquals(IndexOutboxStatus.DONE, entry.getStatus());
    }

    @Test
    void flushPendingRecordsRetryableFailure() throws Exception {
        IndexOutboxEntity entry = new IndexOutboxEntity(
                "out-1",
                "doc-1",
                null,
                IndexOutboxOperation.DELETE_DOC,
                null,
                1L
        );
        when(indexOutboxRepository.findByDocumentIdAndStatusOrderByCreatedAtAsc("doc-1", IndexOutboxStatus.PENDING))
                .thenReturn(List.of(entry));
        when(elasticsearchRetriever.getDocumentIndexVersion("doc-1")).thenReturn(0L);
        org.mockito.Mockito.doThrow(new IOException("es down"))
                .when(elasticsearchRetriever).deleteByDocumentIdOrThrow("doc-1");

        service.flushPendingForDocument("doc-1");

        assertEquals(IndexOutboxStatus.PENDING, entry.getStatus());
        assertEquals(1, entry.getAttemptCount());
    }
}
