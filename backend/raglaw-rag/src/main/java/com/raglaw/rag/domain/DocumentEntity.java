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
@Table(name = "raglaw_document")
public class DocumentEntity {

    @Id
    @Column(length = ColumnLengths.UUID)
    private String id;

    @Column(name = "category_id", nullable = false, length = ColumnLengths.UUID)
    private String categoryId;

    @Column(nullable = false, length = ColumnLengths.TITLE)
    private String title;

    @Column(name = "doc_type", nullable = false, length = ColumnLengths.DOC_TYPE)
    private String docType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = ColumnLengths.DOC_TYPE)
    private DocStatus status = DocStatus.PENDING;

    @Column(name = "ingest_stage", length = 32)
    private String ingestStage;

    @Column(name = "ingest_error", columnDefinition = "TEXT")
    private String ingestError;

    @Column(name = "uploader_id", length = ColumnLengths.UUID)
    private String uploaderId;

    @Column(name = "minio_key", length = ColumnLengths.MINIO_KEY)
    private String minioKey;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @Column(name = "full_text", columnDefinition = "MEDIUMTEXT")
    private String fullText;

    @Column(name = "index_version", nullable = false)
    private long indexVersion;

    @Column(name = "reject_reason", length = ColumnLengths.REJECT_REASON)
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(String id, String categoryId, String title, String docType, String uploaderId, String minioKey) {
        this.id = id;
        this.categoryId = categoryId;
        this.title = title;
        this.docType = docType;
        this.uploaderId = uploaderId;
        this.minioKey = minioKey;
        this.status = DocStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public String getDocType() {
        return docType;
    }

    public DocStatus getStatus() {
        return status;
    }

    public String getUploaderId() {
        return uploaderId;
    }

    public String getMinioKey() {
        return minioKey;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
        this.updatedAt = Instant.now();
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
        this.updatedAt = Instant.now();
    }

    public long getIndexVersion() {
        return indexVersion;
    }

    public void bumpIndexVersion() {
        this.indexVersion += 1;
        this.updatedAt = Instant.now();
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(DocStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
        this.updatedAt = Instant.now();
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
        this.updatedAt = Instant.now();
    }

    public void setDocType(String docType) {
        this.docType = docType;
        this.updatedAt = Instant.now();
    }

    public void setMinioKey(String minioKey) {
        this.minioKey = minioKey;
        this.updatedAt = Instant.now();
    }

    public String getIngestStage() {
        return ingestStage;
    }

    public void setIngestStage(String ingestStage) {
        this.ingestStage = ingestStage;
        this.updatedAt = Instant.now();
    }

    public String getIngestError() {
        return ingestError;
    }

    public void setIngestError(String ingestError) {
        this.ingestError = ingestError;
        this.updatedAt = Instant.now();
    }
}
