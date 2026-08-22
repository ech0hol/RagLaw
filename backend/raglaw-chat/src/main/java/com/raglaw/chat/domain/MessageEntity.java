package com.raglaw.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_message")
public class MessageEntity {

    @Id
    private String id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(nullable = false, length = 16)
    private String role;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "citations_json")
    private String citationsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(
            String id,
            String conversationId,
            String role,
            String content,
            String citationsJson,
            Instant createdAt
    ) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.citationsJson = citationsJson;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
