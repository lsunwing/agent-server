package com.david.agent.memory.longterm;

import reactor.core.publisher.Mono;

import java.util.List;

public interface MemoryExtractor {

    Mono<List<MemoryCandidate>> extract(ExtractionTurn turn);
}
