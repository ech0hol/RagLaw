package com.raglaw.memory.casefile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "raglaw_case")
public class CaseEntity {

    @Id
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected CaseEntity() {
    }

    public CaseEntity(String id, CaseScope scope, String title, Instant now) {
        this.id = require(id, "id");
        this.tenantId = scope.tenantId();
        this.userId = scope.userId();
        this.title = title == null || title.isBlank() ? "未命名案件" : title.strip();
        this.status = "OPEN";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public CaseScope scope() {
        return new CaseScope(tenantId, userId, id);
    }

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void rename(String title, Instant now) {
        this.title = title == null || title.isBlank() ? this.title : title.strip();
        this.updatedAt = now;
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
        return value;
    }
}
