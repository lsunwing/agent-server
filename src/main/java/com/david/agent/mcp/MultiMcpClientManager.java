package com.david.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MultiMcpClientManager implements McpClientManager {

    private final McpProperties mcpProperties;
    private final ObjectMapper objectMapper;
    private final Map<String, StdioMcpClientManager> clients = new HashMap<>();

    public MultiMcpClientManager(McpProperties mcpProperties, ObjectMapper objectMapper) {
        this.mcpProperties = mcpProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        Map<String, McpServerProperties> servers = mcpProperties.servers();
        if (servers == null || servers.isEmpty()) {
            log.info("No MCP servers configured");
            return;
        }

        for (Map.Entry<String, McpServerProperties> entry : servers.entrySet()) {
            String serverName = entry.getKey();
            McpServerProperties serverProps = entry.getValue();

            if (serverProps == null) {
                continue;
            }

            McpServerProperties propsWithName = new McpServerProperties(
                    serverName,
                    serverProps.enabled(),
                    serverProps.command(),
                    serverProps.args(),
                    serverProps.workingDirectory(),
                    serverProps.startupTimeout()
            );

            StdioMcpClientManager client = new StdioMcpClientManager(propsWithName, objectMapper);
            clients.put(serverName, client);
            log.info("MCP client registered: name={}, enabled={}", serverName, serverProps.enabled());
        }
    }

    @PreDestroy
    public void shutdown() {
        clients.values().forEach(StdioMcpClientManager::shutdown);
        clients.clear();
    }

    public StdioMcpClientManager getClient(String serverName) {
        return clients.get(serverName);
    }

    public Map<String, StdioMcpClientManager> getClients() {
        return Map.copyOf(clients);
    }

    @Override
    public Mono<List<McpToolDescriptor>> listTools() {
        return Mono.fromCallable(() -> {
            List<McpToolDescriptor> allTools = new ArrayList<>();
            for (StdioMcpClientManager client : clients.values()) {
                try {
                    List<McpToolDescriptor> tools = client.listTools().block();
                    if (tools != null) {
                        allTools.addAll(tools);
                    }
                } catch (Exception e) {
                    log.error("Failed to list tools from MCP server: {}", client.getServerName(), e);
                }
            }
            return allTools;
        });
    }

    @Override
    public Mono<Object> callTool(String name, Map<String, Object> arguments) {
        return Mono.defer(() -> {
            int dotIndex = name == null ? -1 : name.indexOf('.');
            if (dotIndex > 0) {
                String serverName = name.substring(0, dotIndex);
                String toolName = name.substring(dotIndex + 1);
                StdioMcpClientManager client = clients.get(serverName);
                if (client == null) {
                    return Mono.error(new IllegalArgumentException("MCP server not found: " + serverName));
                }
                return client.callTool(toolName, arguments);
            }

            for (StdioMcpClientManager client : clients.values()) {
                try {
                    return client.callTool(name, arguments);
                } catch (Exception e) {
                    log.debug("Tool {} not found in server {}", name, client.getServerName());
                }
            }
            return Mono.error(new IllegalArgumentException("Tool not found in any MCP server: " + name));
        });
    }

    @Override
    public McpStatus status() {
        return statusList().stream().findFirst().orElse(
                new McpStatus("none", false, false, false, "", List.of(), List.of(), "", null, null, null)
        );
    }

    @Override
    public List<McpStatus> statusList() {
        return clients.values().stream()
                .map(StdioMcpClientManager::status)
                .toList();
    }

    public Mono<List<McpServerDetail>> listServerDetails() {
        return reactor.core.publisher.Flux.fromIterable(clients.values())
                .flatMap(client -> {
                    McpStatus status = client.status();
                    return client.listTools()
                            .map(tools -> new McpServerDetail(
                                    status.serverName(),
                                    status.enabled(),
                                    status.initialized(),
                                    status.processAlive(),
                                    status.command(),
                                    status.args(),
                                    tools != null ? tools : List.of(),
                                    status.lastError(),
                                    status.lastInitializedAt(),
                                    status.lastToolsRefreshAt(),
                                    status.lastCallAt()
                            ))
                            .onErrorResume(e -> {
                                log.error("Failed to list tools for MCP server: {}", status.serverName(), e);
                                return Mono.just(new McpServerDetail(
                                        status.serverName(),
                                        status.enabled(),
                                        status.initialized(),
                                        status.processAlive(),
                                        status.command(),
                                        status.args(),
                                        List.of(),
                                        status.lastError(),
                                        status.lastInitializedAt(),
                                        status.lastToolsRefreshAt(),
                                        status.lastCallAt()
                                ));
                            });
                })
                .collectList();
    }
}

