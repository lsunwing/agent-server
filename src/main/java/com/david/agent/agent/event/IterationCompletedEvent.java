package com.david.agent.agent.event;

import java.time.Instant;

public record IterationCompletedEvent(String conversationId, Instant timestamp, int iteration)
        implements AgentEvent {
}
