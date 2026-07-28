package com.david.agent.tool.discovery;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface ToolRetriever {

    Mono<List<ToolDescriptor>> retrieve(
            String userInput,
            List<ToolDescriptor> candidates,
            Map<String, Object> variables
    );
}
