package com.david.agent.mcp;

import com.david.agent.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolProvider {

    private final McpConnectionProperties properties;
    private final McpClientManager clientManager;
    private final ToolRegistry toolRegistry;

    @PostConstruct
    public void load() {
        if (!properties.enabled()) {
            log.info("MCP github integration disabled. Skip loading MCP tools.");
            return;
        }

        clientManager.listTools()
                .doOnNext(tools -> {
                    log.info("MCP github connected. Discovered {} tools.", tools.size());
                    tools.forEach(tool -> {
                        toolRegistry.register(new McpToolAdapter(clientManager, tool));
                        log.info("MCP tool registered: {}", tool.name());
                    });
                })
                .doOnError(error -> log.error("Load MCP github tools failed", error))
                .onErrorResume(ignored -> Mono.empty())
                .block();
    }
}
