package com.raglaw.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationTitleRequest(
        @NotBlank @Size(max = 255) String title
) {
}
