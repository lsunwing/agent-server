package com.david.agent.mcp;

import java.time.Instant;
import java.util.List;

public record McpStatus(
        String serverName,
        boolean enabled,
        boolean initialized,
        boolean processAlive,
        String command,
        List<String> args,
        List<String> discoveredTools,
        String lastError,
        Instant lastInitializedAt,
        Instant lastToolsRefreshAt,
        Instant lastCallAt
) {
    public McpStatus {
        serverName = serverName == null ? "" : serverName;
        args = args == null ? List.of() : List.copyOf(args);
        discoveredTools = discoveredTools == null ? List.of() : List.copyOf(discoveredTools);
        lastError = lastError == null ? "" : lastError;
    }
}
