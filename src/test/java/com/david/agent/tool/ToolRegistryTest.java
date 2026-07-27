package com.david.agent.tool;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRegistryTest {

    @Test
    void supportsDynamicRegister() {
        ToolRegistry registry = new ToolRegistry(java.util.List.of());

        Tool helloTool = new Tool() {
            @Override
            public String name() {
                return "hello";
            }

            @Override
            public String description() {
                return "hello tool";
            }

            @Override
            public Mono<Object> execute(Map<String, Object> arguments) {
                return Mono.just("ok");
            }
        };

        registry.register(helloTool);

        assertTrue(registry.contains("hello"));
        assertEquals("hello", registry.getRequired("hello").name());
        assertEquals(1, registry.definitions().size());
    }
}
