package com.david.agent.mcp;

import com.david.agent.tool.Tool;
import reactor.core.publisher.Mono;

import java.util.Map;

public class McpToolAdapter implements Tool {

    private final McpClientManager clientManager;
    private final McpToolDescriptor descriptor;

    public McpToolAdapter(McpClientManager clientManager, McpToolDescriptor descriptor) {
        this.clientManager = clientManager;
        this.descriptor = descriptor;
    }

    @Override
    public String name() {
        return descriptor.name();
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
    public Mono<Object> execute(Map<String, Object> arguments) {
        return clientManager.callTool(name(), arguments == null ? Map.of() : arguments);
    }
}
