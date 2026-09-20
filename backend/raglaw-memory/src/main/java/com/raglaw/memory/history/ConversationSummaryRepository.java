package com.raglaw.memory.history;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationSummaryRepository extends JpaRepository<ConversationSummaryEntity, String> {
    Optional<ConversationSummaryEntity> findTopByTenantIdAndUserIdAndCaseIdAndConversationIdOrderByRevisionDesc(String tenantId, String userId, String caseId, String conversationId);
}
