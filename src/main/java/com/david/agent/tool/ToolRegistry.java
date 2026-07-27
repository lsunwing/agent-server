package com.david.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<Tool> initialTools) {
        initialTools.forEach(this::register);
    }

    public void register(Tool tool) {
        Tool previous = tools.put(tool.name(), tool);
        if (previous == null) {
            log.info("Tool registered: {}", tool.name());
        } else {
            log.info("Tool replaced: {} old={} new={}", tool.name(), previous.getClass().getSimpleName(), tool.getClass().getSimpleName());
        }
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
        return tools.values().stream()
                .sorted(Comparator.comparing(Tool::name))
                .map(ToolDefinition::from)
                .toList();
    }
}
