package com.david.agent.prompt;

import com.david.agent.agent.message.Message;
import com.david.agent.tool.ToolDefinition;

import java.util.List;

public record Prompt(List<Message> messages, List<ToolDefinition> tools) {
    public Prompt {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
