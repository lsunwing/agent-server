package com.david.agent.agent.event;

import com.david.agent.model.ChatResponse;

import java.time.Instant;

public record AgentCompletedEvent(String conversationId, Instant timestamp, ChatResponse response)
        implements AgentEvent {
}
