package com.david.agent.mcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpConnectionProperties.class)
public class McpConnectionConfig {
}
