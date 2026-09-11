package com.david.agent.tool.websearch;

import com.david.agent.tool.websearch.config.TavilyProperties;
import com.david.agent.tool.websearch.model.WebSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "web-search.tavily.enabled", havingValue = "true", matchIfMissing = true)
public class TavilyClient {

    private final WebClient.Builder webClientBuilder;
    private final TavilyProperties properties;

    public Mono<WebSearchResult> search(String query, int maxResults, String searchDepth) {
        String url = properties.baseUrl() + "/search";

        Map<String, Object> body = Map.of(
                "api_key", properties.apiKey(),
                "query", query,
                "max_results", maxResults,
                "search_depth", searchDepth,
                "include_answer", true
        );

        return webClientBuilder.build()
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.parse("PT" + properties.timeout().toUpperCase()))
                .map(this::parseResponse)
                .doOnNext(result -> log.info("[tavily] search ok, query='{}', results={}", query, result.results().size()))
                .onErrorMap(error -> {
                    log.error("[tavily] search failed: {}", error.getMessage());
                    return new WebSearchException("Tavily search failed: " + error.getMessage(), error);
                });
    }

    private WebSearchResult parseResponse(JsonNode root) {
        String answer = root.has("answer") ? root.path("answer").asText("") : "";
        List<WebSearchResult.SearchItem> items = new ArrayList<>();
        JsonNode results = root.path("results");
        if (results.isArray()) {
            for (JsonNode item : results) {
                items.add(new WebSearchResult.SearchItem(
                        item.path("title").asText(""),
                        item.path("url").asText(""),
                        item.path("content").asText("")
                ));
            }
        }
        return new WebSearchResult(root.path("query").asText(""), answer, items);
    }
}
