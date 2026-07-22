package com.david.agent.llm.openai.dto;

public record OpenAIToolCall(String id, String type, OpenAIToolCallFunction function) {
}
