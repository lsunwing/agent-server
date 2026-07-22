package com.david.agent.agent.event;

import java.time.Instant;

public record LLMStartedEvent(String conversationId, Instant timestamp, int iteration)
        implements AgentEvent {
}
