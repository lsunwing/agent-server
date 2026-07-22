package com.david.agent.llm.openai;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.llm.openai.dto.OpenAIChatResponse;
import com.david.agent.llm.openai.dto.OpenAIChoice;
import com.david.agent.llm.openai.dto.OpenAIMessage;
import com.david.agent.llm.openai.dto.OpenAIToolCall;
import com.david.agent.llm.openai.dto.OpenAIToolCallFunction;
import com.david.agent.llm.openai.dto.OpenAIUsage;
import com.david.agent.model.FinishReason;
import com.david.agent.tool.ToolDefinition;
import com.david.agent.prompt.Prompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAIConverterTest {

    private final OpenAIConverter converter = new OpenAIConverter(new ObjectMapper());

    @Test
    void convertsContextWithoutLeakingInternalModel() {
        AgentContext context = AgentContext.builder()
                .messages(List.of(Message.user("hello")))
                .tools(List.of(new ToolDefinition("time", "Current time",
                        Map.of("type", "object", "properties", Map.of()))))
                .build();

        var request = converter.toRequest(new Prompt(context.messages(), context.tools()), "test-model");

        assertEquals("test-model", request.model());
        assertEquals("user", request.messages().get(0).role());
        assertEquals("time", request.tools().get(0).function().name());
    }

    @Test
    void convertsToolCallsAndUsage() {
        OpenAIMessage message = new OpenAIMessage("assistant", null, null, null,
                List.of(new OpenAIToolCall("call-1", "function",
                        new OpenAIToolCallFunction("time", "{}"))));
        OpenAIChatResponse source = new OpenAIChatResponse(
                "response-1",
                List.of(new OpenAIChoice(0, message, "tool_calls")),
                new OpenAIUsage(10, 5, 15));

        var response = converter.toResponse(source);

        assertEquals(FinishReason.TOOL_CALLS, response.finishReason());
        assertEquals("call-1", response.toolCalls().get(0).id());
        assertEquals(15, response.usage().totalTokens());
    }
}
