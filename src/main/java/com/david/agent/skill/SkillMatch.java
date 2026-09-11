package com.david.agent.skill;

public record SkillMatch(
        SkillDefinition skill,
        int score,
        String reason
) {
    public SkillMatch {
        reason = reason == null ? "" : reason;
    }
}
