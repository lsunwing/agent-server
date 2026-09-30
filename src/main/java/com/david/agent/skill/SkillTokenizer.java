package com.david.agent.skill;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SkillTokenizer {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}_]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");

    private SkillTokenizer() {
    }

    /**
     * 分词：英文/数字按词切；中文额外生成 2 字滑动窗口（bigram），
     * 避免整句被当成一个词导致检索/工具匹配全部落空。
     */
    public static Set<String> tokenize(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String part : NON_WORD.split(normalized)) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (containsCjk(token)) {
                addCjkTokens(token, tokens);
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static void addCjkTokens(String token, Set<String> tokens) {
        // 整段保留，便于精确命中
        tokens.add(token);
        String cjkOnly = token.replaceAll("[^\\p{IsHan}]", "");
        if (cjkOnly.length() >= 2) {
            for (int i = 0; i + 1 < cjkOnly.length(); i++) {
                tokens.add(cjkOnly.substring(i, i + 2));
            }
        }
        if (cjkOnly.length() >= 3) {
            for (int i = 0; i + 2 < cjkOnly.length(); i++) {
                tokens.add(cjkOnly.substring(i, i + 3));
            }
        }
        // 混排时再拆出非中文片段
        for (String mixed : token.split("[\\p{IsHan}]+")) {
            if (!mixed.isBlank() && mixed.length() >= 2) {
                tokens.add(mixed);
            }
        }
    }

    private static boolean containsCjk(String token) {
        return CJK.matcher(token).find();
    }
}
