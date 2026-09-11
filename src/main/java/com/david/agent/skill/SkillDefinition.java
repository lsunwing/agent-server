package com.david.agent.skill;

import java.util.List;

public record SkillDefinition(
        String name,
        String description,
        String path,
        String skillFile,
        String instructions,
        List<String> keywords
) {
    public SkillDefinition {
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
        instructions = instructions == null ? "" : instructions.trim();
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
