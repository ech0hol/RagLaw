package com.raglaw.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_category")
public class CategoryEntity {

    @Id
    private String id;

    @Column(name = "parent_id")
    private String parentId;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CategoryEntity() {
    }

    public CategoryEntity(
            String id,
            String parentId,
            int level,
            String code,
            String name,
            String path,
            String docType,
            int sortOrder
    ) {
        this.id = id;
        this.parentId = parentId;
        this.level = level;
        this.code = code;
        this.name = name;
        this.path = path;
        this.docType = docType;
        this.sortOrder = sortOrder;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getParentId() {
        return parentId;
    }

    public int getLevel() {
        return level;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getDocType() {
        return docType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
