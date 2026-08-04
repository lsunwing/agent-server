package com.david.agent.tool.finance.stock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SinaFinanceProperties.class)
public class SinaFinanceConfig {
}
