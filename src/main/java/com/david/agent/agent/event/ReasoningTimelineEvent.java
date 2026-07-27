package com.david.agent.agent.event;

import java.time.Instant;

public record ReasoningTimelineEvent(
        String conversationId,
        Instant timestamp,
        int iteration,
        String stage,
        String message
) implements AgentEvent {
}
