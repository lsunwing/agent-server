package com.david.agent.mcp;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface McpClientManager {

    Mono<List<McpToolDescriptor>> listTools();

    Mono<Object> callTool(String name, Map<String, Object> arguments);

    McpStatus status();

    default List<McpStatus> statusList() {
        return List.of(status());
    }
}
