package com.david.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTokenizerTest {

    @Test
    void chineseQueryIsSplitIntoBigrams() {
        Set<String> tokens = SkillTokenizer.tokenize("智能问答平台建设目标是什么");
        assertTrue(tokens.contains("智能"));
        assertTrue(tokens.contains("问答"));
        assertTrue(tokens.contains("平台"));
        assertTrue(tokens.contains("建设"));
        assertTrue(tokens.contains("目标"));
    }

    @Test
    void englishWordsAreKeptWhole() {
        Set<String> tokens = SkillTokenizer.tokenize("how to use web_search");
        assertTrue(tokens.contains("how"));
        assertTrue(tokens.contains("web_search"));
    }
}
