package com.david.agent.skill;

import java.nio.file.Path;

public interface SkillLoader {

    SkillDefinition load(Path skillFile);
}
