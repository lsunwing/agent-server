package com.david.agent.memory.longterm;

import reactor.core.publisher.Mono;

import java.util.List;

public interface MemoryManager {

    Mono<List<Memory>> saveCandidates(String conversationId, List<MemoryCandidate> candidates);

    Mono<List<Memory>> search(MemoryQuery query);

    Mono<Void> forget(Long id);
}
