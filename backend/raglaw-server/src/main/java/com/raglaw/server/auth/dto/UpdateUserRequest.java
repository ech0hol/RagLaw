package com.raglaw.server.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 128) String displayName,
        boolean enabled,
        @NotBlank @Pattern(regexp = "LAWYER|ADMIN") String role
) {
}
