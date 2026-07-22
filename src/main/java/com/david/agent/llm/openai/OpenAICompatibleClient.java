package com.david.agent.llm.openai;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.llm.LLMClient;
import com.david.agent.llm.LLMProperties;
import com.david.agent.llm.openai.dto.OpenAIChatRequest;
import com.david.agent.llm.openai.dto.OpenAIChatResponse;
import com.david.agent.model.ChatResponse;
import com.david.agent.prompt.PromptBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class OpenAICompatibleClient implements LLMClient {

    private final WebClient webClient;
    private final LLMProperties properties;
    private final OpenAIConverter converter;
    private final PromptBuilder promptBuilder;

    @Override
    public Mono<ChatResponse> chat(AgentContext context) {
        OpenAIChatRequest request = converter.toRequest(promptBuilder.build(context), properties.model());
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAIChatResponse.class)
                .map(converter::toResponse);
    }
}
