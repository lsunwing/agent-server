package com.david.agent.agent.event;

import java.time.Instant;

public record IterationStartedEvent(String conversationId, Instant timestamp, int iteration)
        implements AgentEvent {
}
