package com.raglaw.common.auth;

public final class CurrentUserHolder {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static String get() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
