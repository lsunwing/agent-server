package com.david.agent.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAIUsage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
) {
}
