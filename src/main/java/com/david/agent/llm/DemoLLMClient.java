package com.david.agent.llm;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.model.ChatResponse;
import com.david.agent.model.FinishReason;
import com.david.agent.model.ToolCall;
import reactor.core.publisher.Mono;

import java.util.Map;

public class DemoLLMClient implements LLMClient {

    @Override
    public Mono<ChatResponse> chat(AgentContext context) {
        if (!context.toolResults().isEmpty()) {
            return Mono.just(ChatResponse.builder()
                    .content("Tool results: " + context.toolResults())
                    .finishReason(FinishReason.STOP)
                    .build());
        }

        String userMessage = context.messages().get(context.messages().size() - 1).content();
        if ("/time".equalsIgnoreCase(userMessage.trim())) {
            return Mono.just(ChatResponse.builder()
                    .toolCalls(java.util.List.of(ToolCall.builder()
                            .id("demo-time-call")
                            .name("time")
                            .arguments(Map.of())
                            .build()))
                    .finishReason(FinishReason.TOOL_CALLS)
                    .build());
        }

        return Mono.just(ChatResponse.builder()
                .content("Demo LLM received: " + userMessage)
                .finishReason(FinishReason.STOP)
                .build());
    }
}
