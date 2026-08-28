package com.david.agent.memory.longterm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.long-term-memory.enabled", havingValue = "true", matchIfMissing = true)
public class MemoryExtractionService {

    private static final List<String> SIGNAL_WORDS = List.of(
            "记住", "以后", "默认", "偏好", "喜欢", "讨厌", "项目", "架构", "技术栈", "升级", "改用", "统一"
    );

    private final MemoryExtractor extractor;
    private final MemoryManager memoryManager;
    private final LongTermMemoryProperties properties;

    public Mono<List<Memory>> extractAsync(ExtractionTurn turn) {
        if (!properties.extraction().isEnabled()) {
            log.info("[memory] extraction disabled by config");
            return Mono.just(List.of());
        }
        if (turn == null || turn.userMessage() == null || isBlank(turn.userMessage().content())) {
            log.info("[memory] extraction skipped: blank turn or user message");
            return Mono.just(List.of());
        }
        if (properties.extraction().isGateEnabled() && !gateHit(turn)) {
            log.info("[memory] extraction SKIPPED by gate, conversationId={}, userMsg='{}'",
                    turn.conversationId(), abbreviate(turn.userMessage().content(), 60));
            return Mono.just(List.of());
        }
        log.info("[memory] extraction PASSED gate, conversationId={}, userMsg='{}', answerLen={}",
                turn.conversationId(),
                abbreviate(turn.userMessage().content(), 60),
                turn.finalAnswer() == null ? 0 : turn.finalAnswer().length());
        return extractor.extract(turn)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(candidates -> log.info("[memory] extractor returned {} candidate(s)", candidates.size()))
                .flatMap(candidates -> {
                    if (candidates.isEmpty()) {
                        log.info("[memory] no candidates extracted, nothing to save");
                        return Mono.just(List.<Memory>of());
                    }
                    log.info("[memory] saving {} candidate(s) to store", candidates.size());
                    return memoryManager.saveCandidates(turn.conversationId(), candidates);
                })
                .doOnNext(saved -> {
                    if (!saved.isEmpty()) {
                        log.info("[memory] PIPELINE OK: {} memory/ies saved, conversationId={}", saved.size(), turn.conversationId());
                    } else {
                        log.info("[memory] pipeline completed but 0 memories saved (all filtered or duplicate), conversationId={}", turn.conversationId());
                    }
                })
                .onErrorResume(error -> {
                    log.error("[memory] extraction pipeline FAILED, conversationId={}", turn.conversationId(), error);
                    return Mono.just(List.of());
                });
    }

    private boolean gateHit(ExtractionTurn turn) {
        String text = safe(turn.userMessage().content()) + "\n" + safe(turn.finalAnswer());
        boolean hit = SIGNAL_WORDS.stream().anyMatch(text::contains);
        log.debug("[memory] gate check: hit={}, text='{}'", hit, abbreviate(text, 80));
        return hit;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
