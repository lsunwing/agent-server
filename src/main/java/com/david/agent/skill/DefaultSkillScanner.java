package com.david.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class DefaultSkillScanner implements SkillScanner {

    private static final String SKILL_FILE = "SKILL.md";

    @Override
    public List<Path> scan(Path skillsRoot) {
        if (skillsRoot == null || !Files.isDirectory(skillsRoot)) {
            log.warn("[skill] skills root does not exist or is not a directory: {}", skillsRoot);
            return List.of();
        }

        List<Path> found = new ArrayList<>();
        try (Stream<Path> entries = Files.list(skillsRoot)) {
            entries.filter(Files::isDirectory)
                    .filter(this::notHidden)
                    .forEach(dir -> {
                        Path skillFile = dir.resolve(SKILL_FILE);
                        if (Files.isRegularFile(skillFile)) {
                            found.add(skillFile);
                        } else {
                            log.warn("[skill] directory without {} ignored: {}", SKILL_FILE, dir);
                        }
                    });
        } catch (IOException e) {
            log.warn("[skill] failed to scan skills root: {}", skillsRoot, e);
        }
        return found;
    }

    private boolean notHidden(Path path) {
        return path.getFileName() == null || !path.getFileName().toString().startsWith(".");
    }
}
