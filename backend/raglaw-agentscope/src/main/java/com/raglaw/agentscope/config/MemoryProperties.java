package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "raglaw.memory")
public class MemoryProperties {
    private MemoryMode mode = MemoryMode.SHADOW;
    private int maxCharacters = 8_000;

    public MemoryMode getMode() { return mode; }

    public void setMode(MemoryMode mode) { this.mode = mode == null ? MemoryMode.SHADOW : mode; }

    public int getMaxCharacters() { return maxCharacters; }

    public void setMaxCharacters(int maxCharacters) {
        this.maxCharacters = maxCharacters <= 0 ? 8_000 : Math.min(maxCharacters, 32_000);
    }
}
