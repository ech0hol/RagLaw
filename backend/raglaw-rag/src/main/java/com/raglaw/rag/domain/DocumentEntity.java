package com.raglaw.rag.domain;

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
    private String id;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(nullable = false)
    private String title;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocStatus status = DocStatus.PENDING;

    @Column(name = "uploader_id")
    private String uploaderId;

    @Column(name = "minio_key")
    private String minioKey;

    @Column(name = "reject_reason")
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
}
