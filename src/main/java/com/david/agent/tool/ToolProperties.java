package com.david.agent.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tool")
public record ToolProperties(Duration timeout) {
    public ToolProperties {
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }
}
