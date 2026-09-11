package com.david.agent.tool.websearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "web-search.tavily")
public record TavilyProperties(
        Boolean enabled,
        String apiKey,
        String baseUrl,
        Integer maxResults,
        String searchDepth,
        String timeout
) {
    public TavilyProperties {
        enabled = enabled == null || enabled;
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.tavily.com" : baseUrl;
        maxResults = maxResults == null || maxResults < 1 ? 5 : maxResults;
        searchDepth = searchDepth == null || searchDepth.isBlank() ? "basic" : searchDepth;
        timeout = timeout == null || timeout.isBlank() ? "10s" : timeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
