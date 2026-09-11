package com.david.agent.tool.websearch;

import com.david.agent.tool.websearch.config.TavilyProperties;
import com.david.agent.tool.websearch.model.WebSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "web-search.tavily.enabled", havingValue = "true", matchIfMissing = true)
public class WebSearchService {

    private final TavilyClient tavilyClient;
    private final TavilyProperties properties;

    public Mono<WebSearchResult> search(String query) {
        return search(query, properties.maxResults(), properties.searchDepth());
    }

    public Mono<WebSearchResult> search(String query, int maxResults, String searchDepth) {
        if (!properties.hasApiKey()) {
            log.warn("[tavily] API key not configured, search disabled");
            return Mono.error(new WebSearchException("Tavily API key not configured. Set web-search.tavily.api-key"));
        }
        return tavilyClient.search(query, maxResults, searchDepth);
    }
}
