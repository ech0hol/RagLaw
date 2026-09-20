package com.raglaw.agentscope.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoryPropertiesTest {
    @Test
    void defaultsToShadowAndBoundsBudget() {
        MemoryProperties properties = new MemoryProperties();
        assertThat(properties.getMode()).isEqualTo(MemoryMode.SHADOW);
        properties.setMode(null);
        properties.setMaxCharacters(100_000);
        assertThat(properties.getMode()).isEqualTo(MemoryMode.SHADOW);
        assertThat(properties.getMaxCharacters()).isEqualTo(32_000);
    }
}
