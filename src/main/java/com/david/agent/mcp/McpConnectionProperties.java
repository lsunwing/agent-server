package com.david.agent.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "mcp.github")
public record McpConnectionProperties(
        boolean enabled,
        String command,
        List<String> args,
        String workingDirectory,
        Duration startupTimeout
) {
    public McpConnectionProperties {
        command = command == null || command.isBlank() ? "npx" : command;
        args = args == null ? List.of("@modelcontextprotocol/server-github") : List.copyOf(args);
        startupTimeout = startupTimeout == null ? Duration.ofSeconds(10) : startupTimeout;
    }
}
