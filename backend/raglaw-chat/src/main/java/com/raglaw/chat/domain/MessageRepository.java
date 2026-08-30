package com.raglaw.chat.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    long countByConversationIdAndRole(String conversationId, String role);

    java.util.Optional<MessageEntity> findTopByConversationIdAndRoleOrderByCreatedAtDesc(
            String conversationId,
            String role
    );

    java.util.Optional<MessageEntity> findByIdAndConversationId(String id, String conversationId);

    void deleteByConversationIdAndCreatedAtGreaterThanEqual(String conversationId, java.time.Instant createdAt);
}
