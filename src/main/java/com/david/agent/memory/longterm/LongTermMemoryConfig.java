package com.david.agent.memory.longterm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LongTermMemoryProperties.class)
public class LongTermMemoryConfig {
}
