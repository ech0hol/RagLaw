package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AgentscopeLlmProperties.class, AgentscopeMcpProperties.class})
public class AgentscopeConfig {
}
