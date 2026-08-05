package com.david.agent.mcp;

import com.david.agent.tool.Tool;
import com.david.agent.tool.discovery.ToolMetadata;
import reactor.core.publisher.Mono;

import java.util.Map;

public class McpToolAdapter implements Tool {

    private final McpClientManager clientManager;
    private final String namespace;
    private final McpToolDescriptor descriptor;

    public McpToolAdapter(McpClientManager clientManager, String namespace, McpToolDescriptor descriptor) {
        this.clientManager = clientManager;
        this.namespace = namespace == null ? "" : namespace.trim();
        this.descriptor = descriptor;
    }

    @Override
    public String name() {
        if (namespace.isBlank()) {
            return descriptor.name();
        }
        return namespace + "." + descriptor.name();
    }

    @Override
    public String description() {
        return descriptor.description();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return descriptor.inputSchema();
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.mcp(name(), description());
    }

    @Override
    public Mono<Object> execute(Map<String, Object> arguments) {
        return clientManager.callTool(descriptor.name(), arguments == null ? Map.of() : arguments);
    }
}

