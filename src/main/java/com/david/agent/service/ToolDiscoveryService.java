package com.david.agent.service;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.MessageRole;
import com.david.agent.log.AgentFlowLogger;
import com.david.agent.tool.ToolDefinition;
import com.david.agent.tool.discovery.ToolCatalog;
import com.david.agent.tool.discovery.ToolDescriptor;
import com.david.agent.tool.discovery.ToolRetriever;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolDiscoveryService {

    private static final int DEFAULT_MAX_TOOLS = 8;

    /** 无匹配时的兜底：只给通用内置工具，避免误带 filesystem/github 等 MCP */
    private static final List<String> FALLBACK_TOOL_NAMES =
            List.of("memory", "web_search", "time", "weather", "stock");

    private final ToolCatalog toolCatalog;
    private final List<ToolRetriever> retrievers;

    public Mono<AgentContext> discoverForContext(AgentContext context) {
        String userInput = latestUserMessage(context);

        return discoverToolDefinitionsWithReason(userInput, context.variables())
                .map(result -> {
                    AgentFlowLogger.toolSelect(context.conversationId(), result.reason(), result.names());
                    return context.toBuilder().tools(result.definitions()).build();
                });
    }

    public Mono<List<ToolDefinition>> discoverToolDefinitions(String userInput, Map<String, Object> variables) {
        return discoverToolDefinitionsWithReason(userInput, variables).map(ToolSelectResult::definitions);
    }

    public Mono<ToolSelectResult> discoverToolDefinitionsWithReason(String userInput, Map<String, Object> variables) {
        List<ToolDescriptor> candidates = toolCatalog.listDescriptors();

        if (candidates.isEmpty()) {
            return Mono.just(new ToolSelectResult(List.of(), "no-candidates"));
        }

        if (retrievers == null || retrievers.isEmpty()) {
            List<ToolDefinition> fallback = fallbackTools(candidates);
            return Mono.just(new ToolSelectResult(fallback, "no-retriever-fallback-generic"));
        }

        return Flux.fromIterable(retrievers)
                .concatMap(retriever -> retriever.retrieve(userInput, candidates, variables))
                .collectList()
                .map(results -> merge(results, candidates));
    }

    private ToolSelectResult merge(List<List<ToolDescriptor>> results, List<ToolDescriptor> candidates) {
        Map<String, ToolDefinition> selected = new LinkedHashMap<>();

        for (List<ToolDescriptor> list : results) {
            for (ToolDescriptor descriptor : list) {
                selected.putIfAbsent(descriptor.name(), descriptor.definition());
                if (selected.size() >= DEFAULT_MAX_TOOLS) {
                    return new ToolSelectResult(List.copyOf(selected.values()), "keyword-matched");
                }
            }
        }

        if (!selected.isEmpty()) {
            return new ToolSelectResult(List.copyOf(selected.values()), "keyword-matched");
        }

        return new ToolSelectResult(fallbackTools(candidates), "no-match-fallback-generic");
    }

    private List<ToolDefinition> fallbackTools(List<ToolDescriptor> candidates) {
        Map<String, ToolDefinition> byName = new LinkedHashMap<>();
        for (ToolDescriptor descriptor : candidates) {
            byName.put(descriptor.name(), descriptor.definition());
        }
        List<ToolDefinition> fallback = new ArrayList<>();
        for (String name : FALLBACK_TOOL_NAMES) {
            ToolDefinition definition = byName.get(name);
            if (definition != null) {
                fallback.add(definition);
            }
        }
        return fallback;
    }

    private String latestUserMessage(AgentContext context) {
        Optional<String> latest = context.messages().stream()
                .filter(message -> message.role() == MessageRole.USER)
                .map(message -> message.content() == null ? "" : message.content())
                .reduce((first, second) -> second);
        return latest.orElse("");
    }

    public record ToolSelectResult(List<ToolDefinition> definitions, String reason) {
        public List<String> names() {
            return definitions.stream().map(ToolDefinition::name).toList();
        }
    }
}
