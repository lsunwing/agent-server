package com.david.agent.tool.weather.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenMeteoProperties.class)
public class OpenMeteoConfig {
}
