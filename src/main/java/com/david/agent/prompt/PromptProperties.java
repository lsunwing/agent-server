package com.david.agent.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prompt")
public record PromptProperties(String system) {
    public PromptProperties {
        system = system == null ? "" : system;
    }
}
