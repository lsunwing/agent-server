package com.david.agent.controller;

import com.david.agent.mcp.McpClientManager;
import com.david.agent.mcp.McpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {

    private final McpClientManager mcpClientManager;

    @GetMapping("/status")
    public List<McpStatus> status() {
        return mcpClientManager.statusList();
    }
}
