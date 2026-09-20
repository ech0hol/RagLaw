package com.raglaw.chat.dto;

public record CreateConversationRequest(String agentCode, String contextDocumentId, String caseId) {
    public CreateConversationRequest(String agentCode, String contextDocumentId) {
        this(agentCode, contextDocumentId, null);
    }
}
