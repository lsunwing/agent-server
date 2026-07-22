package com.david.agent.tool.executor;

import java.util.Map;

public record ToolExecution(String toolCallId, String toolName, Map<String, Object> arguments) {
    public ToolExecution {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
