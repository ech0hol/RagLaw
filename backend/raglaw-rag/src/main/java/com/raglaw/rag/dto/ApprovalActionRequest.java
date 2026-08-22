package com.raglaw.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record ApprovalActionRequest(
        @NotBlank String reason
) {
}
