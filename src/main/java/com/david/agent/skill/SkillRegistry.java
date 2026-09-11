package com.david.agent.skill;

import java.util.List;

public interface SkillRegistry {

    void register(SkillDefinition skill);

    void unregister(String name);

    SkillDefinition get(String name);

    List<SkillDefinition> getAll();

    boolean contains(String name);
}
