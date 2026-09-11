package com.david.agent.skill;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DefaultSkillRegistry implements SkillRegistry {

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    @Override
    public void register(SkillDefinition skill) {
        if (skill == null || skill.name().isBlank()) {
            return;
        }
        skills.put(skill.name(), skill);
    }

    @Override
    public void unregister(String name) {
        if (name != null) {
            skills.remove(name);
        }
    }

    @Override
    public SkillDefinition get(String name) {
        return skills.get(name);
    }

    @Override
    public List<SkillDefinition> getAll() {
        return new ArrayList<>(skills.values());
    }

    @Override
    public boolean contains(String name) {
        return name != null && skills.containsKey(name);
    }
}
