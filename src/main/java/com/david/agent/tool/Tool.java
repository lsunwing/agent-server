package com.david.agent.tool;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface Tool {

    String name();

    String description();

    default Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    Mono<Object> execute(Map<String, Object> arguments);
}
