package com.raglaw.server.auth;

public record AuthUser(String id, String email, String displayName, String role) {
}
