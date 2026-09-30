package com.david.agent.document.retrieval;

import com.david.agent.document.config.RagProperties;
import com.david.agent.document.model.RagChunk;
import com.david.agent.document.store.DocumentStore;
import com.david.agent.skill.SkillTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class KeywordDocumentRetriever implements DocumentRetriever {

    private static final int KEYWORD_HIT_SCORE = 3;
    private static final int FILEPATH_HIT_SCORE = 1;
    private static final int PHRASE_HIT_SCORE = 5;

    private final DocumentStore documentStore;
    private final RagProperties properties;

    @Override
    public Mono<List<RagChunk>> retrieve(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) {
            return Mono.just(List.of());
        }

        Set<String> queryTokens = SkillTokenizer.tokenize(userQuery);
        if (queryTokens.isEmpty()) {
            return Mono.just(List.of());
        }

        String queryLower = userQuery.toLowerCase(Locale.ROOT);
        // 先用多个关键词扩大候选，再在内存里精排
        List<String> searchTerms = pickSearchTerms(queryTokens, queryLower);
        int limit = Math.max(properties.maxResults() * 4, 20);

        return documentStore.searchChunksAny(searchTerms, limit)
                .map(chunks -> {
                    log.info("[rag] retrieval started, query='{}', terms={}, candidates={}",
                            abbreviate(userQuery, 60), searchTerms.size(), chunks.size());
                    List<ScoredChunk> scored = chunks.stream()
                            .map(chunk -> score(queryTokens, queryLower, chunk))
                            .filter(s -> s.score() > 0)
                            .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                            .limit(properties.maxResults())
                            .toList();

                    if (scored.isEmpty()) {
                        log.info("[rag] no chunks matched");
                    } else {
                        log.info("[rag] retrieved {} chunks, top score={}", scored.size(), scored.get(0).score());
                    }
                    return scored.stream().map(ScoredChunk::chunk).toList();
                });
    }

    /**
     * 选取有区分度的检索词：优先 2–4 字中文片段和完整英文词，避免超长短语导致 LIKE 全落空。
     */
    private List<String> pickSearchTerms(Set<String> queryTokens, String queryLower) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : queryTokens) {
            if (token.length() >= 2 && token.length() <= 8) {
                terms.add(token);
            } else if (token.length() > 8) {
                // 超长中文串切几段
                for (int i = 0; i + 4 <= token.length() && terms.size() < 8; i += 2) {
                    terms.add(token.substring(i, Math.min(i + 4, token.length())));
                }
            }
        }
        if (terms.isEmpty()) {
            String fallback = queryLower.length() > 24 ? queryLower.substring(0, 24) : queryLower;
            terms.add(fallback.trim());
        }
        return terms.stream().limit(8).toList();
    }

    private ScoredChunk score(Set<String> queryTokens, String queryLower, RagChunk chunk) {
        int score = 0;
        String content = chunk.content() == null ? "" : chunk.content().toLowerCase(Locale.ROOT);
        String filePathLower = chunk.filePath() == null ? "" : chunk.filePath().toLowerCase(Locale.ROOT);
        Set<String> chunkTokens = SkillTokenizer.tokenize(content + " " + filePathLower);

        for (String token : queryTokens) {
            if (chunkTokens.contains(token)) {
                score += KEYWORD_HIT_SCORE;
            } else if (token.length() >= 2 && content.contains(token)) {
                // 子串命中：覆盖中文切分不齐的情况
                score += KEYWORD_HIT_SCORE - 1;
            }
            if (filePathLower.contains(token)) {
                score += FILEPATH_HIT_SCORE;
            }
        }

        // 原问句片段整句出现在内容中时额外加分
        if (queryLower.length() >= 4 && content.contains(queryLower)) {
            score += PHRASE_HIT_SCORE;
        }
        return new ScoredChunk(chunk, score);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private record ScoredChunk(RagChunk chunk, int score) {
    }
}
