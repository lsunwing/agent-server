package com.david.agent.llm.openai;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.llm.openai.dto.OpenAIChatRequest;
import com.david.agent.llm.openai.dto.OpenAIChatResponse;
import com.david.agent.llm.openai.dto.OpenAIChoice;
import com.david.agent.llm.openai.dto.OpenAIFunction;
import com.david.agent.llm.openai.dto.OpenAIMessage;
import com.david.agent.llm.openai.dto.OpenAITool;
import com.david.agent.llm.openai.dto.OpenAIToolCall;
import com.david.agent.llm.openai.dto.OpenAIToolCallFunction;
import com.david.agent.model.ChatResponse;
import com.david.agent.model.FinishReason;
import com.david.agent.model.ToolCall;
import com.david.agent.model.Usage;
import com.david.agent.prompt.Prompt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAIConverter {

    private final ObjectMapper objectMapper;

    public OpenAIChatRequest toRequest(Prompt prompt, String model) {
        List<OpenAIMessage> messages = prompt.messages().stream().map(this::toMessage).toList();
        List<OpenAITool> tools = prompt.tools().stream()
                .map(tool -> new OpenAITool("function",
                        new OpenAIFunction(tool.name(), tool.description(), tool.inputSchema())))
                .toList();
        return new OpenAIChatRequest(model, messages, tools, tools.isEmpty() ? null : "auto");
    }

    public ChatResponse toResponse(OpenAIChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI-compatible response contains no choices");
        }

        OpenAIChoice choice = response.choices().get(0);
        OpenAIMessage message = choice.message();
        List<ToolCall> calls = message.toolCalls() == null ? List.of() : message.toolCalls().stream()
                .map(this::toToolCall)
                .toList();
        Usage usage = response.usage() == null ? null : Usage.builder()
                .promptTokens(response.usage().promptTokens())
                .completionTokens(response.usage().completionTokens())
                .totalTokens(response.usage().totalTokens())
                .build();

        return ChatResponse.builder()
                .content(message.content())
                .finishReason(toFinishReason(choice.finishReason()))
                .toolCalls(calls)
                .usage(usage)
                .build();
    }

    private OpenAIMessage toMessage(Message message) {
        List<OpenAIToolCall> calls = message.toolCalls().stream().map(this::toOpenAIToolCall).toList();
        return new OpenAIMessage(
                message.role().name().toLowerCase(Locale.ROOT),
                message.content(),
                message.name(),
                message.toolCallId(),
                calls);
    }

    private OpenAIToolCall toOpenAIToolCall(ToolCall call) {
        return new OpenAIToolCall(call.id(), "function",
                new OpenAIToolCallFunction(call.name(), writeArguments(call.arguments())));
    }

    private ToolCall toToolCall(OpenAIToolCall call) {
        if (call.function() == null) {
            throw new IllegalStateException("Tool call is missing its function");
        }
        return ToolCall.builder()
                .id(call.id())
                .name(call.function().name())
                .arguments(readArguments(call.function().arguments()))
                .build();
    }

    private FinishReason toFinishReason(String reason) {
        if (reason == null) {
            return FinishReason.STOP;
        }
        return switch (reason) {
            case "tool_calls", "function_call" -> FinishReason.TOOL_CALLS;
            case "length" -> FinishReason.LENGTH;
            case "content_filter" -> FinishReason.CONTENT_FILTER;
            case "stop" -> FinishReason.STOP;
            default -> FinishReason.ERROR;
        };
    }

    private String writeArguments(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tool arguments", exception);
        }
    }

    private Map<String, Object> readArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to parse tool arguments", exception);
        }
    }
}
