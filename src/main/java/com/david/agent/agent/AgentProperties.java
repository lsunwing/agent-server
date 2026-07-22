package com.david.agent.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(int maxIterations) {

    public AgentProperties {
        if (maxIterations < 1) {
            maxIterations = 8;
        }
    }
}
