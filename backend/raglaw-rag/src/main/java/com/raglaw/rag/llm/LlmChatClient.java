package com.raglaw.rag.llm;

public interface LlmChatClient {

    String completeJson(String systemPrompt, String userMessage, String model);
}
