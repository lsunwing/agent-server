package com.david.agent.tool;

import java.util.Map;

public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
    public ToolDefinition {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }

    public static ToolDefinition from(Tool tool) {
        return new ToolDefinition(tool.name(), tool.description(), tool.inputSchema());
    }
}
