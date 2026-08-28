package com.david.agent.memory.longterm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultMemoryManager implements MemoryManager {

    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]|[A-Za-z0-9_]+");
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            Pattern.compile("sk-[A-Za-z0-9]{6,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bearer\\s+\\S+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(api[_\\-]?key|apikey|access[_\\-]?token|client[_\\-]?secret)\\s*[:=]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(password|passwd|pwd)\\s*[:=]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(authorization|cookie|session[_\\-]?id)\\s*[:=]", Pattern.CASE_INSENSITIVE)
    );

    private final LongTermMemoryStore store;
    private final LongTermMemoryProperties properties;

    @Override
    public Mono<List<Memory>> saveCandidates(String conversationId, List<MemoryCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(candidates)
                .concatMap(candidate -> saveOne(conversationId, candidate))
                .collectList();
    }

    @Override
    public Mono<List<Memory>> search(MemoryQuery query) {
        return store.findActive(query == null ? new MemoryQuery(null, null, null, null) : query);
    }

    @Override
    public Mono<Void> forget(Long id) {
        return store.updateStatus(id, MemoryStatus.ARCHIVED).then();
    }

    private Mono<Memory> saveOne(String conversationId, MemoryCandidate candidate) {
        if (candidate.content().isBlank()) {
            log.debug("[memory-mgr] skip: blank content");
            return Mono.empty();
        }
        if (isSensitive(candidate.content())) {
            log.warn("[memory-mgr] REJECTED: sensitive content detected, content='{}'",
                    abbreviate(candidate.content(), 60));
            return Mono.empty();
        }
        int minImportance = properties.extraction().minImportance();
        if (candidate.importance() < minImportance) {
            log.debug("[memory-mgr] DROPPED: importance {} < {}", candidate.importance(), minImportance);
            return Mono.empty();
        }
        log.info("[memory-mgr] saving candidate: type={}, key='{}', importance={}, content='{}'",
                candidate.type(), candidate.memoryKey(), candidate.importance(),
                abbreviate(candidate.content(), 60));
        return store.findActiveByType(candidate.type())
                .map(active -> resolveTarget(active, candidate))
                .flatMap(target -> persist(conversationId, candidate, target));
    }

    private Optional<Memory> resolveTarget(List<Memory> active, MemoryCandidate candidate) {
        for (Memory memory : active) {
            if (candidate.content().equals(memory.content())) {
                log.info("[memory-mgr] exact content match, skip (already active), existingId={}", memory.id());
                return Optional.of(memory);
            }
        }
        String normalizedKey = normalizeKey(candidate.memoryKey(), candidate.content());
        for (Memory memory : active) {
            if (normalizedKey.equals(normalizeKey(memory.memoryKey(), memory.content()))) {
                log.info("[memory-mgr] exact key match, will replace, existingId={}, key='{}'", memory.id(), normalizedKey);
                return Optional.of(memory);
            }
        }
        Set<String> candidateTokens = tokenize(candidate.content());
        for (Memory memory : active) {
            Set<String> tokens = tokenize(memory.content() + " " + safe(memory.memoryKey()));
            double sim = jaccard(candidateTokens, tokens);
            if (sim >= SIMILARITY_THRESHOLD) {
                log.info("[memory-mgr] similarity match ({}), will replace, existingId={}", String.format("%.2f", sim), memory.id());
                return Optional.of(memory);
            }
        }
        log.info("[memory-mgr] no conflict found, will insert new record");
        return Optional.empty();
    }

    private Mono<Memory> persist(String conversationId, MemoryCandidate candidate, Optional<Memory> target) {
        if (target.isEmpty()) {
            return insertNew(conversationId, candidate);
        }
        Memory existing = target.get();
        if (existing.content().equals(candidate.content())) {
            log.debug("[memory] identical memory already active, id={}", existing.id());
            return Mono.just(existing);
        }
        String key = hasText(existing.memoryKey())
                ? existing.memoryKey()
                : normalizeKey(candidate.memoryKey(), candidate.content());
        log.info("[memory] replacing memory id={} type={} key={}", existing.id(), existing.type(), key);
        return store.updateStatus(existing.id(), MemoryStatus.ARCHIVED)
                .then(insertNew(conversationId, withKey(candidate, key)));
    }

    private MemoryCandidate withKey(MemoryCandidate candidate, String key) {
        return new MemoryCandidate(candidate.type(), key, candidate.content(), candidate.importance());
    }

    private Mono<Memory> insertNew(String conversationId, MemoryCandidate candidate) {
        Memory memory = Memory.newActive(
                candidate.type(),
                normalizeKey(candidate.memoryKey(), candidate.content()),
                candidate.content(),
                clampImportance(candidate.importance()),
                conversationId
        );
        return store.insert(memory)
                .doOnNext(saved -> log.info("[memory] saved id={} type={} key={} importance={}",
                        saved.id(), saved.type(), saved.memoryKey(), saved.importance()))
                .filter(Objects::nonNull);
    }

    private int clampImportance(int importance) {
        return Math.max(1, Math.min(10, importance));
    }

    private boolean isSensitive(String content) {
        return SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }

    private String normalizeKey(String key, String fallbackContent) {
        String cleaned = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (!cleaned.isBlank()) {
            return cleaned;
        }
        return "auto_" + shortHash(fallbackContent);
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
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

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
