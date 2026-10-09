package com.david.agent.document.retrieval;

import com.david.agent.document.config.RagProperties;
import com.david.agent.document.model.RagChunk;
import com.david.agent.document.store.DocumentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.rag.enabled", havingValue = "true", matchIfMissing = true)
public class KeywordDocumentRetriever implements DocumentRetriever {

    private final DocumentStore documentStore;
    private final QueryAnalyzer queryAnalyzer;
    private final RagProperties properties;

    @Override
    public Mono<List<RagChunk>> retrieve(String userQuery) {
        log.info("[rag] >>> retrieve() called, query='{}'", abbreviate(userQuery, 80));
        if (userQuery == null || userQuery.isBlank()) {
            log.info("[rag] <<< retrieve() skip: blank query");
            return Mono.just(List.of());
        }

        int limit = Math.max(properties.maxResults() * 4, 20);

        return queryAnalyzer.extractKeywords(userQuery)
                .flatMap(keywords -> {
                    log.info("[rag] extractKeywords returned {} keywords: {}", keywords.size(), keywords);
                    if (keywords.isEmpty()) {
                        log.info("[rag] <<< retrieve() skip: no keywords");
                        return Mono.just(List.of());
                    }
                    return documentStore.searchChunksAny(keywords, limit)
                            .map(chunks -> {
                                log.info("[rag] searchChunksAny returned {} candidates", chunks.size());
                                List<ScoredChunk> scored = chunks.stream()
                                        .map(chunk -> score(keywords, chunk))
                                        .filter(s -> s.score() > 0)
                                        .sorted(Comparator.comparingInt(ScoredChunk::score).reversed())
                                        .limit(properties.maxResults())
                                        .toList();

                                if (scored.isEmpty()) {
                                    log.info("[rag] <<< retrieve() 0 chunks after scoring");
                                } else {
                                    log.info("[rag] <<< retrieve() {} chunks, top score={}", scored.size(), scored.get(0).score());
                                }
                                return scored.stream().map(ScoredChunk::chunk).toList();
                            });
                });
    }

    private ScoredChunk score(List<String> keywords, RagChunk chunk) {
        String content = chunk.content() == null ? "" : chunk.content().toLowerCase(Locale.ROOT);
        String filePathLower = chunk.filePath() == null ? "" : chunk.filePath().toLowerCase(Locale.ROOT);

        int matched = 0;
        int totalScore = 0;
        for (String keyword : keywords) {
            String kw = keyword.toLowerCase(Locale.ROOT);
            boolean inContent = content.contains(kw);
            boolean inPath = filePathLower.contains(kw);
            if (inContent || inPath) {
                matched++;
                totalScore += inContent ? 3 : 1;
            }
        }

        double coverage = (double) matched / keywords.size();
        totalScore += (int) (coverage * 10);

        return new ScoredChunk(chunk, totalScore);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private record ScoredChunk(RagChunk chunk, int score) {
    }
}
