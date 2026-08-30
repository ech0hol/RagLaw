package com.raglaw.rag.domain;

import com.raglaw.common.jpa.ColumnLengths;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_index_outbox")
public class IndexOutboxEntity {

    @Id
    @Column(length = ColumnLengths.UUID)
    private String id;

    @Column(name = "document_id", nullable = false, length = ColumnLengths.UUID)
    private String documentId;

    @Column(name = "chunk_id", length = ColumnLengths.UUID)
    private String chunkId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IndexOutboxOperation operation;

    @Column(name = "payload_json", columnDefinition = "JSON")
    private String payloadJson;

    @Column(name = "index_version", nullable = false)
    private long indexVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IndexOutboxStatus status = IndexOutboxStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IndexOutboxEntity() {
    }

    public IndexOutboxEntity(
            String id,
            String documentId,
            String chunkId,
            IndexOutboxOperation operation,
            String payloadJson,
            long indexVersion
    ) {
        this.id = id;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.operation = operation;
        this.payloadJson = payloadJson;
        this.indexVersion = indexVersion;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public IndexOutboxOperation getOperation() {
        return operation;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public long getIndexVersion() {
        return indexVersion;
    }

    public IndexOutboxStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void markDone() {
        this.status = IndexOutboxStatus.DONE;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void markFailed(String errorMessage) {
        this.status = IndexOutboxStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }

    public boolean recordAttemptFailure(String errorMessage, int maxAttempts) {
        this.attemptCount += 1;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
        if (this.attemptCount >= maxAttempts) {
            this.status = IndexOutboxStatus.FAILED;
            return true;
        }
        this.status = IndexOutboxStatus.PENDING;
        return false;
    }

    public void resetForRetry() {
        this.status = IndexOutboxStatus.PENDING;
        this.updatedAt = Instant.now();
    }
}
