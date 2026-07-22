package com.david.agent.agent.event;

import java.time.Instant;

public interface AgentEvent {

    default String getType() {
        return getClass().getSimpleName();
    }

    String conversationId();

    Instant timestamp();
}
