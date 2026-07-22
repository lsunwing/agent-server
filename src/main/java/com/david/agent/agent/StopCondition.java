package com.david.agent.agent;

import com.david.agent.model.ChatResponse;

public interface StopCondition {

    boolean shouldStop(ChatResponse response);
}
