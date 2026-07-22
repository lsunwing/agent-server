package com.david.agent.agent.event;

import java.time.Instant;

public record AgentTokenEvent(String conversationId, Instant timestamp, String token)
        implements AgentEvent {
}
