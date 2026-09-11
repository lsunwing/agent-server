package com.david.agent.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownSkillLoaderTest {

    private final MarkdownSkillLoader loader = new MarkdownSkillLoader();

    @Test
    void parsesFrontMatterAndInstructions(@TempDir Path dir) throws Exception {
        String content = """
                ---
                name: stock-analysis
                description: 分析股票走势
                keywords: [股票, 行情, stock]
                ---

                # Stock Analysis

                ## Workflow
                1. query history
                """;
        Path file = dir.resolve("SKILL.md");
        Files.writeString(file, content);

        SkillDefinition skill = loader.load(file);

        assertEquals("stock-analysis", skill.name());
        assertEquals("分析股票走势", skill.description());
        assertEquals(3, skill.keywords().size());
        assertTrue(skill.instructions().contains("Stock Analysis"));
    }

    @Test
    void throwsWhenNameMissing(@TempDir Path dir) throws Exception {
        String content = """
                ---
                description: 无名字
                ---

                body
                """;
        Path file = dir.resolve("SKILL.md");
        Files.writeString(file, content);

        assertThrows(SkillLoadException.class, () -> loader.load(file));
    }

    @Test
    void throwsWhenDescriptionMissing(@TempDir Path dir) throws Exception {
        String content = """
                ---
                name: no-desc
                ---

                body
                """;
        Path file = dir.resolve("SKILL.md");
        Files.writeString(file, content);

        assertThrows(SkillLoadException.class, () -> loader.load(file));
    }

    @Test
    void treatsWholeFileAsInstructionsWhenNoFrontMatter(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("SKILL.md");
        Files.writeString(file, "plain body");

        assertThrows(SkillLoadException.class, () -> loader.load(file));
    }
}
