package com.raglaw.server.auth;

public final class UserContext {
    private static final ThreadLocal<AuthUser> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(AuthUser user) {
        CURRENT.set(user);
    }

    public static AuthUser get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
