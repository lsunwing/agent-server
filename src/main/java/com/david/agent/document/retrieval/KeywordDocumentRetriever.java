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
        int limit = properties.maxResults() * 3;

        return documentStore.searchChunks(queryLower, limit)
                .map(chunks -> {
                    log.info("[rag] retrieval started, query='{}', candidates={}", abbreviate(userQuery, 60), chunks.size());
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

    private ScoredChunk score(Set<String> queryTokens, String queryLower, RagChunk chunk) {
        int score = 0;
        Set<String> chunkTokens = SkillTokenizer.tokenize(chunk.content() + " " + chunk.filePath());
        for (String token : queryTokens) {
            if (chunkTokens.contains(token)) {
                score += KEYWORD_HIT_SCORE;
            }
        }
        String filePathLower = chunk.filePath().toLowerCase(Locale.ROOT);
        for (String token : queryTokens) {
            if (filePathLower.contains(token)) {
                score += FILEPATH_HIT_SCORE;
            }
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
