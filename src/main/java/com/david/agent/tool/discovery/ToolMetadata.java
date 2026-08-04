package com.david.agent.tool.discovery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record ToolMetadata(
        ToolSourceType sourceType,
        List<String> keywords,
        List<String> intents,
        int priority
) {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);

    public ToolMetadata {
        sourceType = sourceType == null ? ToolSourceType.LOCAL : sourceType;
        keywords = normalize(keywords);
        intents = normalize(intents);
    }

    public ToolMetadata withKeywords(List<String> extraKeywords) {
        Set<String> merged = new LinkedHashSet<>(keywords);
        merged.addAll(normalize(extraKeywords));
        return new ToolMetadata(sourceType, List.copyOf(merged), intents, priority);
    }

    public ToolMetadata withPriority(int newPriority) {
        return new ToolMetadata(sourceType, keywords, intents, newPriority);
    }

    public static ToolMetadata local(String toolName, String description, List<String> extraKeywords) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(tokenize(toolName));
        keywords.addAll(tokenize(description));
        if (extraKeywords != null) {
            extraKeywords.forEach(value -> keywords.addAll(tokenize(value)));
        }
        return new ToolMetadata(ToolSourceType.LOCAL, List.copyOf(keywords), List.of(), 100);
    }

    public static ToolMetadata mcp(String toolName, String description) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(tokenize(toolName));
        keywords.addAll(tokenize(description));
        return new ToolMetadata(ToolSourceType.MCP, List.copyOf(keywords), List.of("mcp"), 80);
    }

    public static ToolMetadata remoteApi(String toolName, String description) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(tokenize(toolName));
        keywords.addAll(tokenize(description));
        return new ToolMetadata(ToolSourceType.REMOTE_API, List.copyOf(keywords), List.of("remote-api"), 90);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String cleaned = value.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) {
                tokens.add(cleaned);
            }
        }
        return List.copyOf(tokens);
    }

    private static List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        String[] parts = NON_WORD.split(normalized);
        Set<String> result = new LinkedHashSet<>();
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return List.copyOf(result);
    }
}
