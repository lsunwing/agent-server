package com.david.agent.service;

import com.david.agent.agent.AgentExecutor;
import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentCompletedEvent;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.agent.event.ReasoningTimelineEvent;
import com.david.agent.agent.message.Message;
import com.david.agent.agent.message.MessageRole;
import com.david.agent.memory.MessageStore;
import com.david.agent.memory.longterm.ExtractionTurn;
import com.david.agent.memory.longterm.Memory;
import com.david.agent.memory.longterm.MemoryExtractionService;
import com.david.agent.memory.longterm.MemoryPromptRenderer;
import com.david.agent.memory.longterm.MemoryRetriever;
import com.david.agent.model.ChatRequest;
import com.david.agent.model.ChatResponse;
import com.david.agent.model.FinishReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<MemoryRetriever> memoryRetrieverProvider;
    private final ObjectProvider<MemoryExtractionService> memoryExtractionServiceProvider;
    private final ObjectProvider<MemoryPromptRenderer> memoryPromptRendererProvider;

    public Mono<ChatResponse> chat(ChatRequest request) {
        return retrieveMemories(request.message())
                .flatMap(memories -> {
                    AgentContext context = createContext(request, memories);
                    return agentExecutor.execute(context)
                            .doOnSuccess(response -> hookExtraction(response, context));
                });
    }

    public Flux<AgentEvent> stream(ChatRequest request) {
        return retrieveMemories(request.message())
                .flatMapMany(memories -> {
                    AgentContext context = createContext(request, memories);
                    String conversationId = context.conversationId();

                    log.info("[memory] stream start, conversationId={}, injectedMemories={}", conversationId, memories.size());

                    return agentExecutor.stream(context)
                            .doOnNext(event -> {
                                hookExtraction(event, context);
                            })
                            .doOnError(error -> {
                                log.error("[memory] stream error, extraction skipped, conversationId={}", conversationId, error);
                            })
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
                });
    }

    private AgentContext createContext(ChatRequest request, List<Memory> memories) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();
        List<Message> messages = new ArrayList<>(messageStore.history(conversationId));
        if (!memories.isEmpty()) {
            MemoryPromptRenderer renderer = memoryPromptRendererProvider.getIfAvailable();
            if (renderer != null) {
                messages.add(0, Message.builder()
                        .role(MessageRole.SYSTEM)
                        .content(renderer.render(memories))
                        .build());
            }
        }
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

    private Mono<List<Memory>> retrieveMemories(String userQuery) {
        MemoryRetriever retriever = memoryRetrieverProvider.getIfAvailable();
        if (retriever == null) {
            return Mono.just(List.of());
        }
        return retriever.retrieve(userQuery)
                .onErrorResume(error -> {
                    log.warn("Memory retrieval failed, continue without memories", error);
                    return Mono.just(List.of());
                });
    }

    private void hookExtraction(AgentEvent event, AgentContext context) {
        if (event instanceof AgentCompletedEvent completed) {
            log.info("[memory] hookExtraction triggered, conversationId={}, contentLen={}",
                    context.conversationId(),
                    completed.response() == null || completed.response().content() == null
                            ? 0 : completed.response().content().length());
            hookExtraction(completed.response(), context);
        }
    }

    private void hookExtraction(ChatResponse response, AgentContext context) {
        if (response == null || response.content() == null || response.content().isBlank()) {
            log.info("[memory] hookExtraction skipped: blank response, conversationId={}", context.conversationId());
            return;
        }
        MemoryExtractionService extractionService = memoryExtractionServiceProvider.getIfAvailable();
        if (extractionService == null) {
            log.debug("[memory] hookExtraction skipped: no MemoryExtractionService bean");
            return;
        }
        Message userMessage = lastUserMessage(context);
        if (userMessage == null) {
            log.info("[memory] hookExtraction skipped: no user message in context, conversationId={}", context.conversationId());
            return;
        }
        log.info("[memory] hookExtraction submitting, conversationId={}, userMsg='{}', answerLen={}",
                context.conversationId(),
                abbreviate(userMessage.content(), 50),
                response.content().length());
        ExtractionTurn turn = new ExtractionTurn(
                context.conversationId(),
                userMessage,
                response.content()
        );
        extractionService.extractAsync(turn)
                .subscribe(
                        saved -> {
                            if (!saved.isEmpty()) {
                                log.info("[memory] extraction pipeline completed, {} memory(s) saved, conversationId={}",
                                        saved.size(), context.conversationId());
                            } else {
                                log.info("[memory] extraction pipeline completed, 0 memories saved, conversationId={}",
                                        context.conversationId());
                            }
                        },
                        error -> log.error("[memory] extraction subscribe error", error)
                );
    }

    private Message lastUserMessage(AgentContext context) {
        List<Message> messages = context.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == MessageRole.USER) {
                return messages.get(i);
            }
        }
        return null;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
