package com.david.agent.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.skills")
public record SkillProperties(
        Boolean enabled,
        String path,
        Integer maxResults,
        Integer minScore
) {
    public SkillProperties {
        enabled = enabled == null || enabled;
        path = path == null || path.isBlank() ? "./skills" : path;
        maxResults = maxResults == null || maxResults < 1 ? 5 : maxResults;
        minScore = minScore == null || minScore < 1 ? 2 : minScore;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
