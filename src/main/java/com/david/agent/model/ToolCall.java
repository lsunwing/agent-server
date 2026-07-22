package com.david.agent.model;

import lombok.Builder;

import java.util.Map;

@Builder
public record ToolCall(String id, String name, Map<String, Object> arguments) {
    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
