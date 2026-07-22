package com.david.agent.service;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.message.Message;
import com.david.agent.agent.AgentExecutor;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.memory.MessageStore;
import com.david.agent.model.ChatRequest;
import com.david.agent.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
        return agentExecutor.stream(createContext(request));
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
