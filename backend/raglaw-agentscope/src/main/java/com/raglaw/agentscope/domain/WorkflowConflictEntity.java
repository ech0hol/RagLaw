package com.raglaw.agentscope.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_workflow_conflict", indexes = @Index(name = "idx_workflow_conflict_run", columnList = "run_id"))
public class WorkflowConflictEntity {
    @Id @Column(length = 36) private String id;
    @Column(name = "run_id", nullable = false, length = 36) private String runId;
    @Column(name = "node_result_ids_json", nullable = false, columnDefinition = "JSON") private String nodeResultIdsJson;
    @Column(nullable = false, length = 256) private String slot;
    @Column(name = "conflict_type", nullable = false, length = 64) private String conflictType;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getRunId() { return runId; } public void setRunId(String value) { runId = value; }
    public String getNodeResultIdsJson() { return nodeResultIdsJson; } public void setNodeResultIdsJson(String value) { nodeResultIdsJson = value; }
    public String getSlot() { return slot; } public void setSlot(String value) { slot = value; }
    public String getConflictType() { return conflictType; } public void setConflictType(String value) { conflictType = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
