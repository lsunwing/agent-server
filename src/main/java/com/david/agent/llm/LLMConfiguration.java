package com.david.agent.llm;

import com.david.agent.llm.openai.OpenAICompatibleClient;
import com.david.agent.llm.openai.OpenAIConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import com.david.agent.prompt.PromptBuilder;

@Configuration
public class LLMConfiguration {

    @Bean
    LLMClient llmClient(
            LLMProperties properties,
            WebClient.Builder webClientBuilder,
            OpenAIConverter converter,
            PromptBuilder promptBuilder
    ) {
        if (properties.isDemo()) {
            return new DemoLLMClient();
        }

        WebClient.Builder builder = webClientBuilder.baseUrl(properties.baseUrl());
        if (StringUtils.hasText(properties.apiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        return new OpenAICompatibleClient(builder.build(), properties, converter, promptBuilder);
    }
}
