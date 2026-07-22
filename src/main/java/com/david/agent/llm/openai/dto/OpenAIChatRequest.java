package com.david.agent.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpenAIChatRequest(
        String model,
        List<OpenAIMessage> messages,
        List<OpenAITool> tools,
        @JsonProperty("tool_choice") String toolChoice
) {
}
