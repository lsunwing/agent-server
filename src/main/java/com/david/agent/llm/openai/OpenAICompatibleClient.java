package com.david.agent.llm.openai;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.llm.LLMClient;
import com.david.agent.llm.LLMProperties;
import com.david.agent.llm.openai.dto.OpenAIChatRequest;
import com.david.agent.llm.openai.dto.OpenAIChatResponse;
import com.david.agent.log.AgentFlowLogger;
import com.david.agent.model.ChatResponse;
import com.david.agent.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public class OpenAICompatibleClient implements LLMClient {

    private final WebClient webClient;
    private final LLMProperties properties;
    private final OpenAIConverter converter;
    private final PromptBuilder promptBuilder;

    @Override
    public Mono<ChatResponse> chat(AgentContext context) {
        var prompt = promptBuilder.build(context);
        OpenAIChatRequest request = converter.toRequest(prompt, properties.model());
        int iteration = context.toolCalls().size();
        String conversationId = context.conversationId();
        AtomicInteger messageCount = new AtomicInteger(prompt.messages().size());

        AgentFlowLogger.prompt(
                conversationId,
                !context.ragChunks().isEmpty(),
                context.activeSkill() != null,
                context.tools().size(),
                context.messages().size()
        );
        AgentFlowLogger.llmRequest(
                conversationId,
                iteration,
                properties.model(),
                messageCount.get(),
                context.tools().size()
        );

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAIChatResponse.class)
                .map(converter::toResponse)
                .doOnNext(response -> AgentFlowLogger.llmResponse(
                        conversationId,
                        iteration,
                        String.valueOf(response.finishReason()),
                        response.toolCalls() == null ? 0 : response.toolCalls().size(),
                        response.content() == null ? 0 : response.content().length(),
                        response.content() != null && !response.content().isBlank()
                ));
    }
}
