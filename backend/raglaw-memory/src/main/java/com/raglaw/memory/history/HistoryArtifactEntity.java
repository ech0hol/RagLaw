package com.raglaw.memory.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name="raglaw_history_artifact")
public class HistoryArtifactEntity {
    @Id private String id;
    @Column(name="tenant_id", nullable=false) private String tenantId;
    @Column(name="user_id", nullable=false) private String userId;
    @Column(name="case_id") private String caseId;
    @Column(name="conversation_id") private String conversationId;
    @Column(name="workflow_run_id") private String workflowRunId;
    @Column(name="node_code") private String nodeCode;
    @Column(name="source_id", nullable=false) private String sourceId;
    @Column(name="content_type", nullable=false) private String contentType;
    @Column(name="pointer_uri", nullable=false) private String pointerUri;
    @Column(name="byte_size") private Long byteSize;
    @Column(name="token_estimate") private Integer tokenEstimate;
    @Column(nullable=false) private String checksum;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    protected HistoryArtifactEntity() {}
    public HistoryArtifactEntity(String id,String tenantId,String userId,String caseId,String conversationId,String workflowRunId,String nodeCode,String sourceId,String contentType,String pointerUri,Long byteSize,Integer tokenEstimate,String checksum,Instant createdAt){this.id=id;this.tenantId=tenantId;this.userId=userId;this.caseId=caseId;this.conversationId=conversationId;this.workflowRunId=workflowRunId;this.nodeCode=nodeCode;this.sourceId=sourceId;this.contentType=contentType;this.pointerUri=pointerUri;this.byteSize=byteSize;this.tokenEstimate=tokenEstimate;this.checksum=checksum;this.createdAt=createdAt;}
    public String getId(){return id;} public String getSourceId(){return sourceId;} public String getPointerUri(){return pointerUri;} public String getChecksum(){return checksum;}
}
