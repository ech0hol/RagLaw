package com.raglaw.server.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UserDto user
) {
}
