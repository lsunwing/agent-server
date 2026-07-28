package com.david.agent.tool.discovery;

import com.david.agent.tool.ToolDefinition;

public record ToolDescriptor(
        ToolDefinition definition,
        ToolMetadata metadata
) {
    public ToolDescriptor {
        metadata = metadata == null ? ToolMetadata.local(
                definition == null ? "" : definition.name(),
                definition == null ? "" : definition.description(),
                java.util.List.of()) : metadata;
    }

    public String name() {
        return definition.name();
    }
}
