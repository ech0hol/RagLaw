package com.raglaw.memory.context;

import org.springframework.stereotype.Service;

@Service
public class ContextBudgetPolicy {
    private final double outputReserveRatio;
    public ContextBudgetPolicy() { this(0.25); }
    ContextBudgetPolicy(double outputReserveRatio) {
        if (outputReserveRatio < 0.20 || outputReserveRatio > 0.80) throw new IllegalArgumentException("output reserve must be between 20% and 80%");
        this.outputReserveRatio = outputReserveRatio;
    }
    public ContextBudget budgetFor(ModelWindow window, NodeContextProfile profile) {
        if (window == null || profile == null) throw new IllegalArgumentException("window/profile");
        int reserve = Math.max(1, (int) Math.ceil(window.totalTokens() * outputReserveRatio));
        return new ContextBudget(window.totalTokens() - reserve, reserve);
    }
}
