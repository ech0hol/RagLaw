package com.raglaw.memory.context;

public record ContextBudget(int inputLimit, int outputReserve) {
    public ContextBudget { if (inputLimit <= 0 || outputReserve <= 0) throw new IllegalArgumentException("budget"); }
}
