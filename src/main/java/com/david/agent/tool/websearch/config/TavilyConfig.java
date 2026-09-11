package com.david.agent.tool.websearch.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "web-search.tavily.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TavilyProperties.class)
public class TavilyConfig {
}
