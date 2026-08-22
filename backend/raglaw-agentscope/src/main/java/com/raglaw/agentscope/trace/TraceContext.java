package com.raglaw.agentscope.trace;

import java.util.UUID;

public record TraceContext(
        String traceId,
        String messageId
) {

    public static TraceContext create() {
        return new TraceContext(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
}
