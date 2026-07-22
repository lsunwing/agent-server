package com.david.agent.controller;

import com.david.agent.service.ToolService;
import com.david.agent.tool.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolService toolService;

    @GetMapping
    public List<ToolDefinition> list() {
        return toolService.definitions();
    }

    @PostMapping("/{toolName}/execute")
    public Mono<Object> execute(
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> arguments
    ) {
        return toolService.execute(toolName, arguments);
    }
}
