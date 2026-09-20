package com.raglaw.memory.context;

public record ModelWindow(int totalTokens) {
    public ModelWindow { if (totalTokens <= 0) throw new IllegalArgumentException("totalTokens"); }
}
