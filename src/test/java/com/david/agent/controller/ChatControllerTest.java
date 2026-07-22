package com.david.agent.controller;

import com.david.agent.model.ChatResponse;
import com.david.agent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatService chatService;

    @Test
    void acceptsChatRequest() {
        when(chatService.chat(any())).thenReturn(Mono.just(ChatResponse.builder().content("ok").build()));

        webTestClient.post()
                .uri("/chat")
                .bodyValue("{\"message\":\"hello\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isEqualTo("ok")
                .jsonPath("$.finishReason").isEqualTo("STOP")
                .jsonPath("$.toolCalls").isArray();
    }
}
