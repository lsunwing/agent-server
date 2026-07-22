package com.david.agent.llm.openai.dto;

import java.util.Map;

public record OpenAIFunction(String name, String description, Map<String, Object> parameters) {
}
