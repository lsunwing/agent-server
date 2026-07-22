package com.david.agent.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

@Builder(toBuilder = true)
public record ChatRequest(
        String conversationId,
        @NotBlank String message,
        Map<String, Object> context
) {
    public ChatRequest {
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
