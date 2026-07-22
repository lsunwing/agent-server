package com.david.agent.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAIChoice(
        int index,
        OpenAIMessage message,
        @JsonProperty("finish_reason") String finishReason
) {
}
