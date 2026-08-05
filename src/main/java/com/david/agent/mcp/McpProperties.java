package com.david.agent.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "mcp")
public record McpProperties(Map<String, McpServerProperties> servers) {
    public McpProperties {
        servers = servers == null ? Map.of() : Map.copyOf(servers);
    }
}
