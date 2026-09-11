package com.david.agent.skill;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "agent.skills.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SkillProperties.class)
public class SkillConfig {
}
