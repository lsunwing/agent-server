package com.david.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSkillScannerTest {

    private final DefaultSkillScanner scanner = new DefaultSkillScanner();

    @Test
    void scansOnlyFirstLevelSkillDirectoriesWithSkillFile(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("stock-analysis"));
        Files.writeString(root.resolve("stock-analysis").resolve("SKILL.md"), "x");
        Files.createDirectories(root.resolve("code-review"));
        Files.writeString(root.resolve("code-review").resolve("SKILL.md"), "x");
        Files.createDirectories(root.resolve("no-skill"));
        Files.writeString(root.resolve("no-skill").resolve("README.md"), "x");
        Files.createDirectories(root.resolve(".hidden"));
        Files.writeString(root.resolve(".hidden").resolve("SKILL.md"), "x");

        List<Path> found = scanner.scan(root);

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(path -> path.endsWith("SKILL.md")));
    }

    @Test
    void returnsEmptyForMissingRoot() {
        List<Path> found = scanner.scan(Path.of("/nonexistent/path/skills"));
        assertTrue(found.isEmpty());
    }
}
