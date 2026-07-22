package com.david.agent.agent.event;

import com.david.agent.model.ToolCall;

import java.time.Instant;

public record ToolStartedEvent(String conversationId, Instant timestamp, ToolCall toolCall)
        implements AgentEvent {
}
