package com.david.agent.mcp;

import java.time.Instant;
import java.util.List;

public record McpServerDetail(
        String name,
        boolean enabled,
        boolean initialized,
        boolean processAlive,
        String command,
        List<String> args,
        List<McpToolDescriptor> tools,
        String lastError,
        Instant lastInitializedAt,
        Instant lastToolsRefreshAt,
        Instant lastCallAt
) {
    public McpServerDetail {
        name = name == null ? "" : name;
        args = args == null ? List.of() : List.copyOf(args);
        tools = tools == null ? List.of() : List.copyOf(tools);
        lastError = lastError == null ? "" : lastError;
    }
}
