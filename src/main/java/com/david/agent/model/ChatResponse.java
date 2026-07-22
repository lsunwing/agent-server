package com.david.agent.model;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ChatResponse(
        String conversationId,
        String content,
        FinishReason finishReason,
        List<ToolCall> toolCalls,
        Usage usage
) {
    public ChatResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        finishReason = finishReason == null ? FinishReason.STOP : finishReason;
    }

    public boolean requiresTool() {
        return !toolCalls.isEmpty();
    }
}
