package com.david.agent.agent.event;

import com.david.agent.model.ToolResult;

import java.time.Instant;

public record ToolCompletedEvent(String conversationId, Instant timestamp, ToolResult result)
        implements AgentEvent {
}
