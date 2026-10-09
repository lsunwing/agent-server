package com.david.agent.document.retrieval;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.agent.message.MessageRole;
import com.david.agent.llm.LLMClient;
import com.hankcs.hanlp.seg.common.Term;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class LlmQueryAnalyzer implements QueryAnalyzer {

    private static final String EXTRACTION_INSTRUCTION = """
            你是搜索关键词提取器。从用户问题中提取用于知识库检索的关键词。
            要求：
            1. 提取 3-8 个搜索关键词（含同义词、近义词）
            2. 每个关键词 2-6 个字
            3. 覆盖问题的核心概念
            只输出关键词，用逗号分隔，不要其他任何内容。

            示例：
            问题：数据资产一张图智能问数建设方案，目前现状是什么？
            输出：数据资产,一张图,智能问数,建设方案,现状,当前情况

            问题：%s
            """;

    private final LLMClient llmClient;

    @Override
    public Mono<List<String>> extractKeywords(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return Mono.just(List.of());
        }

        String prompt = String.format(EXTRACTION_INSTRUCTION, userQuery.trim());
        AgentContext context = AgentContext.builder()
                .conversationId("query-analyzer")
                .messages(List.of(
                        Message.builder().role(MessageRole.USER).content(prompt).build()
                ))
                .tools(List.of())
                .build();

        log.info("[rag] calling LLM for keyword extraction, query='{}'", abbreviate(userQuery, 60));

        return llmClient.chat(context)
                .map(response -> {
                    String raw = response == null || response.content() == null ? "" : response.content();
                    log.info("[rag] LLM raw output ({} chars): '{}'", raw.length(), abbreviate(raw, 200));
                    return parseKeywords(raw, userQuery);
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(error -> {
                    log.warn("[rag] LLM query analysis failed ({}), using HanLP fallback", error.getMessage());
                    return Mono.just(fallbackKeywords(userQuery));
                });
    }

    private List<String> parseKeywords(String llmOutput, String originalQuery) {
        if (llmOutput == null || llmOutput.isBlank()) {
            log.warn("[rag] LLM returned empty output, using fallback");
            return fallbackKeywords(originalQuery);
        }

        String cleaned = llmOutput.trim();
        int fenceStart = cleaned.indexOf("```");
        if (fenceStart >= 0) {
            int newline = cleaned.indexOf('\n', fenceStart);
            cleaned = newline >= 0 ? cleaned.substring(newline + 1) : cleaned;
            int fenceEnd = cleaned.lastIndexOf("```");
            if (fenceEnd >= 0) {
                cleaned = cleaned.substring(0, fenceEnd);
            }
        }

        List<String> keywords = new ArrayList<>();
        for (String part : cleaned.split("[,，、;；\\n]+")) {
            String word = part.trim().replaceAll("[\"'\\[\\]]", "").trim();
            if (word.length() >= 2 && word.length() <= 20) {
                keywords.add(word);
            }
        }

        if (keywords.isEmpty()) {
            log.warn("[rag] LLM output unparseable: '{}', using fallback", abbreviate(llmOutput, 100));
            return fallbackKeywords(originalQuery);
        }

        log.info("[rag] LLM extracted keywords: {}", keywords);
        return keywords.stream().limit(8).toList();
    }

    /**
     * HanLP 分词 + 关键词提取。提取名词、动词、形容词等实义词，过滤虚词。
     */
    private List<String> fallbackKeywords(String query) {
        Set<String> terms = new LinkedHashSet<>();
        List<Term> terms2 = com.hankcs.hanlp.HanLP.segment(query);

        Set<String> stopPos = Set.of("w", "c", "p", "u", "d", "r", "e", "y", "o", "x", "h", "k");

        for (Term term : terms2) {
            String word = term.word.trim();
            String pos = term.nature == null ? "" : term.nature.toString();

            if (word.length() < 2) continue;
            if (stopPos.contains(pos)) continue;

            terms.add(word);
        }

        if (terms.isEmpty()) {
            for (Term term : terms2) {
                if (term.word.trim().length() >= 2) {
                    terms.add(term.word.trim());
                }
            }
        }

        List<String> result = terms.stream().limit(8).toList();
        log.info("[rag] HanLP fallback keywords: {}", result);
        return result;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
