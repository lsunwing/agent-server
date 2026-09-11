package com.david.agent.agent.context;

import com.david.agent.agent.message.Message;
import com.david.agent.model.ToolCall;
import com.david.agent.model.ToolResult;
import com.david.agent.skill.SkillDefinition;
import com.david.agent.tool.ToolDefinition;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public record AgentContext(
        String conversationId,
        List<Message> messages,
        List<ToolDefinition> tools,
        List<ToolCall> toolCalls,
        List<ToolResult> toolResults,
        Map<String, Object> variables,
        SkillDefinition activeSkill
) {
    public AgentContext {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
