package com.david.agent.agent;

import com.david.agent.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
public class DefaultStopCondition implements StopCondition {

    @Override
    public boolean shouldStop(ChatResponse response) {
        return !response.requiresTool();
    }
}
