package com.raglaw.agentscope.mcp;

import com.raglaw.agentscope.config.AgentscopeMcpProperties;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class McpClientFactory {

    private static final Logger log = LoggerFactory.getLogger(McpClientFactory.class);

    private final AgentscopeMcpProperties mcpProperties;

    public McpClientFactory(AgentscopeMcpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    public Optional<McpClientWrapper> buildTavilyClient() {
        if (!mcpProperties.isEnabled()) {
            return Optional.empty();
        }
        String apiKey = mcpProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MCP enabled but TAVILY_API_KEY is empty");
            return Optional.empty();
        }
        try {
            McpClientWrapper client = McpClientBuilder.create("tavily")
                    .streamableHttpTransport(mcpProperties.getEndpoint())
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .timeout(Duration.ofSeconds(30))
                    .initializationTimeout(Duration.ofSeconds(20))
                    .buildSync();
            client.initialize().block();
            return Optional.of(client);
        } catch (Exception e) {
            log.warn("Failed to build Tavily MCP client: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
