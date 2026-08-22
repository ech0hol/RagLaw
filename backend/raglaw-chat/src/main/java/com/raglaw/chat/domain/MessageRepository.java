package com.raglaw.chat.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {

    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    long countByConversationIdAndRole(String conversationId, String role);
}
