package com.raglaw.agentscope.agui.dto;

public record AguiRunRequest(
        String conversationId,
        String message,
        String agentCode,
        Boolean regenerate
) {
}
