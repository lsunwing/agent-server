package com.david.agent.memory.longterm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class KeywordMemoryRetriever implements MemoryRetriever {

    private static final int ALWAYS_RELEVANT_MIN_IMPORTANCE = 7;
    private static final Set<MemoryType> ALWAYS_TYPES = Set.of(MemoryType.PROJECT, MemoryType.PREFERENCE);
    private static final Set<MemoryType> KEYWORD_TYPES = Set.of(MemoryType.USER, MemoryType.FACT, MemoryType.TASK);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9_]+");

    private final LongTermMemoryStore store;
    private final LongTermMemoryProperties properties;

    @Override
    public Mono<List<Memory>> retrieve(String userQuery) {
        return Mono.zip(alwaysRelevant(), keywordRelevant(userQuery))
                .map(tuple -> merge(tuple.getT1(), tuple.getT2()))
                .onErrorResume(error -> {
                    log.warn("[memory] retrieval failed, continue without memories", error);
                    return Mono.just(List.of());
                });
    }

    private Mono<List<ScoredMemory>> alwaysRelevant() {
        return Flux.fromIterable(ALWAYS_TYPES)
                .flatMap(store::findActiveByType)
                .flatMapIterable(list -> list)
                .filter(memory -> memory.importance() >= ALWAYS_RELEVANT_MIN_IMPORTANCE)
                .map(memory -> new ScoredMemory(memory, 0))
                .collectList();
    }

    private Mono<List<ScoredMemory>> keywordRelevant(String userQuery) {
        Set<String> queryTokens = tokenize(userQuery);
        if (queryTokens.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(KEYWORD_TYPES)
                .flatMap(store::findActiveByType)
                .flatMapIterable(list -> list)
                .map(memory -> score(queryTokens, memory))
                .filter(scored -> scored.relevance() > 0)
                .collectList();
    }

    private ScoredMemory score(Set<String> queryTokens, Memory memory) {
        Set<String> memoryTokens = tokenize(memory.content() + " " + safe(memory.memoryKey()));
        int hits = (int) queryTokens.stream().filter(memoryTokens::contains).count();
        return new ScoredMemory(memory, hits);
    }

    private List<Memory> merge(List<ScoredMemory> layer1, List<ScoredMemory> layer2) {
        Map<Long, ScoredMemory> merged = new LinkedHashMap<>();
        Stream.concat(layer1.stream(), layer2.stream())
                .forEach(scored -> merged.merge(scored.memory().id(), scored,
                        (first, second) -> first.relevance() >= second.relevance() ? first : second));
        Comparator<ScoredMemory> byRelevance = Comparator.comparingInt(ScoredMemory::relevance).reversed();
        Comparator<ScoredMemory> byImportance = Comparator.comparingInt((ScoredMemory s) -> s.memory().importance()).reversed();
        Comparator<ScoredMemory> byUpdatedAt = Comparator.comparing(
                (ScoredMemory s) -> safe(s.memory().updatedAt()), Comparator.naturalOrder()).reversed();
        return merged.values().stream()
                .sorted(byRelevance.thenComparing(byImportance).thenComparing(byUpdatedAt))
                .limit(properties.maxInject())
                .map(ScoredMemory::memory)
                .toList();
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredMemory(Memory memory, int relevance) {
    }
}
