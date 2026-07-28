package com.david.agent.service;

import com.david.agent.agent.AgentExecutor;
import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentCompletedEvent;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.agent.event.ReasoningTimelineEvent;
import com.david.agent.agent.message.Message;
import com.david.agent.memory.MessageStore;
import com.david.agent.model.ChatRequest;
import com.david.agent.model.ChatResponse;
import com.david.agent.model.FinishReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AgentExecutor agentExecutor;
    private final ToolService toolService;
    private final MessageStore messageStore;

    public Mono<ChatResponse> chat(ChatRequest request) {
        return agentExecutor.execute(createContext(request));
    }

    public Flux<AgentEvent> stream(ChatRequest request) {
        AgentContext context = createContext(request);
        String conversationId = context.conversationId();

        return agentExecutor.stream(context)
                .onErrorResume(error -> {
                    log.error("Agent stream failed, conversationId={}", conversationId, error);

                    String message = "处理请求时发生错误: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                    ChatResponse errorResponse = ChatResponse.builder()
                            .conversationId(conversationId)
                            .content(message)
                            .finishReason(FinishReason.ERROR)
                            .build();

                    messageStore.append(conversationId, Message.assistant(message, List.of()));

                    return Flux.just(
                            new ReasoningTimelineEvent(conversationId, Instant.now(), -1, "ERROR", message),
                            new AgentCompletedEvent(conversationId, Instant.now(), errorResponse)
                    );
                });
    }

    private AgentContext createContext(ChatRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();
        List<Message> messages = new ArrayList<>(messageStore.history(conversationId));
        Message userMessage = Message.user(request.message());
        messages.add(userMessage);
        messageStore.append(conversationId, userMessage);

        return AgentContext.builder()
                .conversationId(conversationId)
                .messages(messages)
                .tools(toolService.definitions())
                .variables(request.context())
                .build();
    }
}
