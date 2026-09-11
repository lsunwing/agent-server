package com.david.agent.service;

import com.david.agent.agent.AgentExecutor;
import com.david.agent.model.ChatRequest;
import com.david.agent.model.ChatResponse;
import com.david.agent.memory.InMemoryMessageStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    @Test
    void createsContextAndDelegatesToAgentExecutor() {
        AgentExecutor executor = mock(AgentExecutor.class);
        ToolService toolService = mock(ToolService.class);
        when(toolService.definitions()).thenReturn(List.of());
        when(executor.execute(argThat(context ->
                context.messages().size() == 1
                        && "hi".equals(context.messages().get(0).content())
                        && "test".equals(context.variables().get("source")))))
                .thenReturn(Mono.just(ChatResponse.builder().content("hello").build()));
        ChatService service = new ChatService(executor, toolService, new InMemoryMessageStore(),
                emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider());

        StepVerifier.create(service.chat(ChatRequest.builder()
                        .message("hi")
                        .context(java.util.Map.of("source", "test"))
                        .build()))
                .expectNextMatches(response -> "hello".equals(response.content()))
                .verifyComplete();
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public T getIfAvailable() {
                return null;
            }
        };
    }
}
