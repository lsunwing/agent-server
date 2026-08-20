package com.david.agent.controller;

import com.david.agent.mcp.McpServerDetail;
import com.david.agent.mcp.McpStatus;
import com.david.agent.mcp.MultiMcpClientManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final MultiMcpClientManager mcpClientManager;

    @GetMapping("/status")
    public List<McpStatus> status() {
        return mcpClientManager.statusList();
    }

    @GetMapping("/servers")
    public Mono<List<McpServerDetail>> servers() {
        return mcpClientManager.listServerDetails();
    }
}
