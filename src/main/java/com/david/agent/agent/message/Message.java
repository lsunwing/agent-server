package com.david.agent.agent.message;

import lombok.Builder;

import com.david.agent.model.ToolCall;
import com.david.agent.model.ToolResult;

import java.util.List;

@Builder
public record Message(
        MessageRole role,
        String content,
        String name,
        String toolCallId,
        List<ToolCall> toolCalls
) {
    public Message {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message user(String content) {
        return Message.builder().role(MessageRole.USER).content(content).build();
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return Message.builder()
                .role(MessageRole.ASSISTANT)
                .content(content)
                .toolCalls(toolCalls)
                .build();
    }

    public static Message tool(ToolResult result, String content) {
        return Message.builder()
                .role(MessageRole.TOOL)
                .content(content)
                .name(result.toolName())
                .toolCallId(result.toolCallId())
                .build();
    }
}
