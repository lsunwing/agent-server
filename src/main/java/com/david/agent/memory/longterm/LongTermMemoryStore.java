package com.david.agent.memory.longterm;

import reactor.core.publisher.Mono;

import java.util.List;

public interface LongTermMemoryStore {

    Mono<Memory> insert(Memory memory);

    Mono<Memory> getById(Long id);

    Mono<List<Memory>> findActiveByType(MemoryType type);

    Mono<List<Memory>> findActive(MemoryQuery query);

    Mono<Integer> updateStatus(Long id, MemoryStatus status);

    Mono<Integer> deleteById(Long id);
}
