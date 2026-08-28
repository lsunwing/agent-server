package com.david.agent.memory.longterm;

import reactor.core.publisher.Mono;

import java.util.List;

public interface MemoryRetriever {

    Mono<List<Memory>> retrieve(String userQuery);
}
