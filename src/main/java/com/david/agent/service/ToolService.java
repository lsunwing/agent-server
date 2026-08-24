package com.david.agent.service;

import com.david.agent.tool.ToolDefinition;
import com.david.agent.tool.ToolRegistry;
import com.david.agent.tool.executor.ToolExecution;
import com.david.agent.tool.executor.ToolExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    public Mono<Object> execute(String toolName, Map<String, Object> arguments) {
        return execute(null, toolName, arguments);
    }

    public Mono<Object> execute(String toolCallId, String toolName, Map<String, Object> arguments) {
        return toolExecutor.execute(new ToolExecution(toolCallId, toolName, arguments));
    }

    public List<ToolDefinition> definitions() {
        return toolRegistry.definitions();
    }

    public List<ToolDefinition> builtinDefinitions() {
        return toolRegistry.builtinDefinitions();
    }
}
