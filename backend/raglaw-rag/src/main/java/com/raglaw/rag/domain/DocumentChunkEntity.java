package com.raglaw.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_document_chunk")
public class DocumentChunkEntity {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "l1_path", nullable = false)
    private String l1Path;

    @Column(name = "l2_path", nullable = false)
    private String l2Path;

    @Column(name = "l3_path", nullable = false)
    private String l3Path;

    @Column(name = "metadata_json", columnDefinition = "JSON")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentChunkEntity() {
    }

    public DocumentChunkEntity(
            String id,
            String documentId,
            String parentId,
            int chunkIndex,
            String content,
            String l1Path,
            String l2Path,
            String l3Path,
            String metadataJson
    ) {
        this.id = id;
        this.documentId = documentId;
        this.parentId = parentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.l1Path = l1Path;
        this.l2Path = l2Path;
        this.l3Path = l3Path;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getParentId() {
        return parentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getL1Path() {
        return l1Path;
    }

    public String getL2Path() {
        return l2Path;
    }

    public String getL3Path() {
        return l3Path;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
