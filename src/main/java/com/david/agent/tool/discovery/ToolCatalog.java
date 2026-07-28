package com.david.agent.tool.discovery;

import com.david.agent.tool.Tool;
import com.david.agent.tool.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ToolCatalog {

    private final ToolRegistry toolRegistry;

    private final Map<String, ToolDescriptor> externalDescriptors = new ConcurrentHashMap<>();

    public List<ToolDescriptor> listDescriptors() {
        List<ToolDescriptor> localDescriptors = toolRegistry.tools().stream()
                .map(this::toDescriptor)
                .toList();

        Map<String, ToolDescriptor> merged = new ConcurrentHashMap<>();
        localDescriptors.forEach(descriptor -> merged.put(descriptor.name(), descriptor));
        externalDescriptors.forEach(merged::putIfAbsent);

        return merged.values().stream()
                .sorted(Comparator.comparing(ToolDescriptor::name))
                .toList();
    }

    public void registerExternal(ToolDescriptor descriptor) {
        externalDescriptors.put(descriptor.name(), descriptor);
    }

    private ToolDescriptor toDescriptor(Tool tool) {
        return new ToolDescriptor(
                com.david.agent.tool.ToolDefinition.from(tool),
                tool.metadata());
    }
}
