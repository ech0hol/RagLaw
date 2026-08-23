package com.raglaw.server.auth.dto;

import com.raglaw.server.domain.UserEntity;
import java.time.Instant;

public record AdminUserDto(
        String id,
        String email,
        String displayName,
        String role,
        boolean enabled,
        Instant createdAt
) {

    public static AdminUserDto from(UserEntity user) {
        return new AdminUserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
