package com.david.agent.memory.longterm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agent.long-term-memory")
public record LongTermMemoryProperties(
        Boolean enabled,
        Integer maxInject,
        Integer maxContentLength,
        Extraction extraction
) {
    public LongTermMemoryProperties {
        enabled = enabled == null || enabled;
        maxInject = maxInject == null || maxInject < 1 ? 8 : maxInject;
        maxContentLength = maxContentLength == null || maxContentLength < 20 ? 200 : maxContentLength;
        extraction = extraction == null ? new Extraction(null, null, null, null) : extraction;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record Extraction(
            Boolean enabled,
            Boolean gateEnabled,
            Integer minImportance,
            Duration timeout
    ) {
        public Extraction {
            enabled = enabled == null || enabled;
            gateEnabled = gateEnabled == null || gateEnabled;
            minImportance = minImportance == null ? 4 : minImportance;
            timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                    ? Duration.ofSeconds(15)
                    : timeout;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isGateEnabled() {
            return gateEnabled;
        }
    }
}
