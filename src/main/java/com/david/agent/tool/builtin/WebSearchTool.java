package com.david.agent.tool.builtin;

import com.david.agent.tool.Tool;
import com.david.agent.tool.discovery.ToolMetadata;
import com.david.agent.tool.websearch.WebSearchService;
import com.david.agent.tool.websearch.model.WebSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "web-search.tavily.enabled", havingValue = "true", matchIfMissing = true)
public class WebSearchTool implements Tool {

    private final WebSearchService webSearchService;

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "搜索互联网获取实时信息。当用户询问实时新闻、最新事件、或你不确定的事实时调用。需要搜索关键词。";
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.remoteApi(name(), description()).withKeywords(List.of(
                "搜索", "搜一下", "查一下", "查查", "查查看", "最新", "新闻",
                "最近", "帮我看看", "了解一下", "想知道", "是不是真的",
                "search", "google", "web", "find", "lookup",
                "实时", "今日", "今天", "现在", "目前"
        )).withPriority(95);
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "搜索关键词，例如：2026年诺贝尔物理学奖"
                        )
                ),
                "required", java.util.List.of("query")
        );
    }

    @Override
    public Mono<Object> execute(Map<String, Object> arguments) {
        String query = normalizeQuery(arguments.get("query"));
        log.info("[web-search] query='{}'", query);

        return webSearchService.search(query)
                .map(this::toToolPayload)
                .cast(Object.class)
                .onErrorResume(error -> Mono.just(Map.of(
                        "success", false,
                        "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
                )));
    }

    private Map<String, Object> toToolPayload(WebSearchResult result) {
        return Map.of(
                "success", true,
                "query", safe(result.query()),
                "answer", safe(result.answer()),
                "results", result.results().stream()
                        .map(item -> Map.<String, Object>of(
                                "title", safe(item.title()),
                                "url", safe(item.url()),
                                "content", safe(item.content())
                        ))
                        .toList()
        );
    }

    private String normalizeQuery(Object queryArg) {
        if (queryArg == null) {
            return "";
        }
        String value = String.valueOf(queryArg).trim();
        return "null".equalsIgnoreCase(value) ? "" : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
