package com.david.agent.llm.openai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OpenAIMessage(
        String role,
        String content,
        String name,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<OpenAIToolCall> toolCalls
) {
}
