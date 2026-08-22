package com.raglaw.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AppendMessageRequest(
        @NotBlank @Pattern(regexp = "user|assistant|system") String role,
        @NotBlank String content,
        String citationsJson
) {
}
