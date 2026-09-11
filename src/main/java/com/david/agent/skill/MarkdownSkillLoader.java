package com.david.agent.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MarkdownSkillLoader implements SkillLoader {

    private static final String FRONT_MATTER_START = "---";

    private final Yaml yaml = new Yaml();

    @Override
    public SkillDefinition load(Path skillFile) {
        String raw;
        try {
            raw = Files.readString(skillFile);
        } catch (IOException e) {
            throw new SkillLoadException("failed to read skill file: " + skillFile, e);
        }

        String[] parts = splitFrontMatter(raw);
        String frontMatter = parts[0];
        String instructions = parts[1];

        String name = "";
        String description = "";
        List<String> keywords = List.of();

        if (!frontMatter.isBlank()) {
            Map<String, Object> meta = yaml.load(frontMatter);
            if (meta != null) {
                name = stringValue(meta.get("name"));
                description = stringValue(meta.get("description"));
                keywords = listValue(meta.get("keywords"));
            }
        }

        if (name.isBlank()) {
            throw new SkillLoadException("skill missing required frontmatter field 'name': " + skillFile);
        }
        if (description.isBlank()) {
            throw new SkillLoadException("skill missing required frontmatter field 'description': " + skillFile);
        }

        Path dir = skillFile.getParent();
        return new SkillDefinition(
                name,
                description,
                dir == null ? "" : dir.toString(),
                skillFile.toString(),
                instructions,
                keywords
        );
    }

    private String[] splitFrontMatter(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (!content.startsWith(FRONT_MATTER_START)) {
            return new String[]{"", content};
        }
        int end = content.indexOf(FRONT_MATTER_START, FRONT_MATTER_START.length());
        if (end < 0) {
            return new String[]{"", content};
        }
        String frontMatter = content.substring(FRONT_MATTER_START.length(), end).trim();
        String instructions = content.substring(end + FRONT_MATTER_START.length()).trim();
        return new String[]{frontMatter, instructions};
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<String> listValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::stringValue)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
