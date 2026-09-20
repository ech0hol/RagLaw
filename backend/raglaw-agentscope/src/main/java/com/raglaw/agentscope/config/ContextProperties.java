package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "raglaw.context")
public class ContextProperties {
    private ContextMode mode = ContextMode.SHADOW;
    private int modelWindowTokens = 32_000;

    public ContextMode getMode() { return mode; }
    public void setMode(ContextMode mode) { this.mode = mode == null ? ContextMode.SHADOW : mode; }
    public int getModelWindowTokens() { return modelWindowTokens; }
    public void setModelWindowTokens(int value) { modelWindowTokens = value <= 0 ? 32_000 : Math.min(value, 200_000); }
}
