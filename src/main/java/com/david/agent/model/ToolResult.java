package com.david.agent.model;

import lombok.Builder;

@Builder
public record ToolResult(String toolCallId, String toolName, Object output) {
}
