package com.david.agent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LLMProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model
) {
    public LLMProperties {
        provider = provider == null || provider.isBlank() ? "demo" : provider;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        apiKey = apiKey == null ? "" : apiKey;
        model = model == null || model.isBlank() ? "gpt-4.1-mini" : model;
    }

    public boolean isDemo() {
        return "demo".equalsIgnoreCase(provider);
    }
}
