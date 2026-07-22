package com.david.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> tools) {
        this.tools = tools.stream()
                .collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity()));
    }

    public Tool getRequired(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new ToolNotFoundException(name);
        }
        return tool;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(ToolDefinition::from).toList();
    }
}
