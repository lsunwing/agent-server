package com.david.agent.tool;

import com.david.agent.tool.discovery.ToolMetadata;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface Tool {

    String name();

    String description();

    default Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    default ToolMetadata metadata() {
        return ToolMetadata.local(name(), description(), List.of());
    }

    Mono<Object> execute(Map<String, Object> arguments);
}
