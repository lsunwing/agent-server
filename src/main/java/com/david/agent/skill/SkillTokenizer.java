package com.david.agent.skill;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SkillTokenizer {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);

    private SkillTokenizer() {
    }

    public static Set<String> tokenize(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String part : NON_WORD.split(normalized)) {
            String token = part.trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
