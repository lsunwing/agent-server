package com.david.agent.mcp;

import java.time.Duration;
import java.util.List;

public record McpServerProperties(
        String name,
        boolean enabled,
        String command,
        List<String> args,
        String workingDirectory,
        Duration startupTimeout
) {
    public McpServerProperties {
        command = command == null || command.isBlank() ? "npx" : command;
        args = args == null ? List.of() : List.copyOf(args);
        startupTimeout = startupTimeout == null ? Duration.ofSeconds(10) : startupTimeout;
    }

    public static McpServerProperties disabled(String name) {
        return new McpServerProperties(name, false, "npx", List.of(), null, null);
    }
}
