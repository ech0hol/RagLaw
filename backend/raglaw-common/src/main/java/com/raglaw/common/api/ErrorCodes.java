package com.raglaw.common.api;

public final class ErrorCodes {
    private ErrorCodes() {
    }

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION = "VALIDATION";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String INTERNAL = "INTERNAL";
    public static final String AGENT_VERSION_CONFLICT = "AGENT_VERSION_CONFLICT";
}
