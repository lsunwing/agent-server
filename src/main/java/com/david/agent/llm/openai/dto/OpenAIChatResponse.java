package com.david.agent.llm.openai.dto;

import java.util.List;

public record OpenAIChatResponse(String id, List<OpenAIChoice> choices, OpenAIUsage usage) {
}
