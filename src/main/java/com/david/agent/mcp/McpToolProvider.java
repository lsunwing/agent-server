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

    private final MultiMcpClientManager multiMcpClientManager;
    private final ToolRegistry toolRegistry;

    @PostConstruct
    public void load() {
        for (var entry : multiMcpClientManager.getClients().entrySet()) {
            String serverName = entry.getKey();
            StdioMcpClientManager client = entry.getValue();

            McpStatus status = client.status();
            if (!status.enabled()) {
                log.info("MCP [{}] integration disabled. Skip loading MCP tools.", serverName);
                continue;
            }

            client.listTools()
                    .doOnNext(tools -> {
                        log.info("MCP [{}] connected. Discovered {} tools.", serverName, tools.size());
                        tools.forEach(tool -> {
                            toolRegistry.register(new McpToolAdapter(client, tool));
                            log.info("MCP [{}] tool registered: {}", serverName, tool.name());
                        });
                    })
                    .doOnError(error -> log.error("Load MCP [{}] tools failed", serverName, error))
                    .onErrorResume(ignored -> Mono.empty())
                    .block();
        }
    }
}
