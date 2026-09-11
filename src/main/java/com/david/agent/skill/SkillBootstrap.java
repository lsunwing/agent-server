package com.david.agent.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.skills.enabled", havingValue = "true", matchIfMissing = true)
public class SkillBootstrap implements ApplicationRunner {

    private final SkillProperties properties;
    private final SkillScanner scanner;
    private final SkillLoader loader;
    private final SkillRegistry registry;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("[skill] skill discovery disabled");
            return;
        }

        Path skillsRoot = Path.of(properties.path());
        List<Path> skillFiles = scanner.scan(skillsRoot);
        log.info("[skill] scanning {}, found {} SKILL.md", skillsRoot, skillFiles.size());

        for (Path skillFile : skillFiles) {
            try {
                SkillDefinition skill = loader.load(skillFile);
                registry.register(skill);
                log.info("[skill] registered: name={}, keywords={}", skill.name(), skill.keywords());
            } catch (SkillLoadException e) {
                log.error("[skill] failed to load skill: {}", skillFile, e);
            }
        }

        log.info("[skill] bootstrap complete, {} skills registered", registry.getAll().size());
    }
}
