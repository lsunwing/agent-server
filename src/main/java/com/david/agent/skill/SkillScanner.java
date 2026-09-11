package com.david.agent.skill;

import java.nio.file.Path;
import java.util.List;

public interface SkillScanner {

    List<Path> scan(Path skillsRoot);
}
