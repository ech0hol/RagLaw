package com.raglaw.rag.contract;

public record ContractChatContext(
        String message,
        boolean injected,
        int textChars,
        int riskCount
) {

    public static ContractChatContext passthrough(String userMessage) {
        return new ContractChatContext(userMessage, false, 0, 0);
    }
}
