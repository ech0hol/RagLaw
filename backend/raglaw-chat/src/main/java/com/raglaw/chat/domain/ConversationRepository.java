package com.raglaw.chat.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

    List<ConversationEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
}
