package com.raglaw.memory.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_conversation_summary")
public class ConversationSummaryEntity {
    @Id private String id;
    @Column(name="tenant_id", nullable=false) private String tenantId;
    @Column(name="user_id", nullable=false) private String userId;
    @Column(name="case_id") private String caseId;
    @Column(name="conversation_id", nullable=false) private String conversationId;
    @Column(nullable=false) private long revision;
    @Column(name="summary_json", nullable=false, columnDefinition="JSON") private String summaryJson;
    @Column(name="source_message_start") private String sourceMessageStart;
    @Column(name="source_message_end") private String sourceMessageEnd;
    @Column(name="model_version") private String modelVersion;
    @Column(name="prompt_version") private String promptVersion;
    @Column(name="input_tokens") private Integer inputTokens;
    @Column(name="output_tokens") private Integer outputTokens;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    protected ConversationSummaryEntity() {}
    public ConversationSummaryEntity(String id, String tenantId, String userId, String caseId, String conversationId, long revision, String summaryJson, String sourceMessageStart, String sourceMessageEnd, String modelVersion, String promptVersion, Integer inputTokens, Integer outputTokens, Instant createdAt) {
        this.id=id; this.tenantId=tenantId; this.userId=userId; this.caseId=caseId; this.conversationId=conversationId; this.revision=revision; this.summaryJson=summaryJson; this.sourceMessageStart=sourceMessageStart; this.sourceMessageEnd=sourceMessageEnd; this.modelVersion=modelVersion; this.promptVersion=promptVersion; this.inputTokens=inputTokens; this.outputTokens=outputTokens; this.createdAt=createdAt;
    }
    public String getId(){return id;} public String getConversationId(){return conversationId;} public long getRevision(){return revision;} public String getSummaryJson(){return summaryJson;} public String getChecksum(){return summaryJson;}
}
