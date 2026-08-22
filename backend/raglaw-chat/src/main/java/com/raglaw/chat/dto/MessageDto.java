package com.raglaw.chat.dto;

import java.time.Instant;

public record MessageDto(
        String id,
        String conversationId,
        String role,
        String content,
        String citationsJson,
        Instant createdAt
) {
}
