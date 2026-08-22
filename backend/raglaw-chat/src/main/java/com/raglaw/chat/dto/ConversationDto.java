package com.raglaw.chat.dto;

import java.time.Instant;

public record ConversationDto(
        String id,
        String userId,
        String title,
        String agentCode,
        Instant createdAt,
        Instant updatedAt
) {
}
