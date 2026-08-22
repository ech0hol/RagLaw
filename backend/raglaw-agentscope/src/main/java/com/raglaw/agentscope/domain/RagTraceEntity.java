package com.raglaw.agentscope.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_rag_trace")
public class RagTraceEntity {

    @Id
    private String id;

    @Column(name = "conversation_id")
    private String conversationId;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "query_text")
    private String queryText;

    @Column(name = "agent_code")
    private String agentCode;

    @Column(name = "langfuse_trace_id")
    private String langfuseTraceId;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RagTraceEntity() {
    }

    public RagTraceEntity(
            String id,
            String conversationId,
            String messageId,
            String userId,
            String queryText,
            String agentCode
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.userId = userId;
        this.queryText = queryText;
        this.agentCode = agentCode;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getUserId() {
        return userId;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
