package com.david.agent.agent.event;

import com.david.agent.model.ChatResponse;

import java.time.Instant;

public record LLMCompletedEvent(
        String conversationId,
        Instant timestamp,
        int iteration,
        ChatResponse response
) implements AgentEvent {
}
