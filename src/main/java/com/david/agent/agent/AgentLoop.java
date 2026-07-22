package com.david.agent.agent;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentCompletedEvent;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.agent.event.IterationCompletedEvent;
import com.david.agent.agent.event.IterationStartedEvent;
import com.david.agent.agent.event.LLMCompletedEvent;
import com.david.agent.agent.event.LLMStartedEvent;
import com.david.agent.agent.event.ToolCompletedEvent;
import com.david.agent.agent.event.ToolStartedEvent;
import com.david.agent.agent.message.Message;
import com.david.agent.llm.LLMClient;
import com.david.agent.memory.MessageStore;
import com.david.agent.model.ChatResponse;
import com.david.agent.model.ToolCall;
import com.david.agent.model.ToolResult;
import com.david.agent.service.ToolService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private final LLMClient llmClient;
    private final ToolService toolService;
    private final StopCondition stopCondition;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final MessageStore messageStore;

    public Flux<AgentEvent> stream(AgentContext context) {
        return run(context, 0);
    }

    private Flux<AgentEvent> run(AgentContext context, int iteration) {
        return Flux.defer(() -> {
            if (iteration >= properties.maxIterations()) {
                return Flux.error(new AgentLoopLimitException(properties.maxIterations()));
            }

            String conversationId = context.conversationId();
            return Flux.concat(
                    Flux.just(new IterationStartedEvent(conversationId, Instant.now(), iteration)),
                    Flux.just(new LLMStartedEvent(conversationId, Instant.now(), iteration)),
                    llmClient.chat(context).flatMapMany(response -> afterLlm(context, iteration, response)));
        });
    }

    private Flux<AgentEvent> afterLlm(AgentContext context, int iteration, ChatResponse response) {
        String conversationId = context.conversationId();
        Flux<AgentEvent> llmCompleted = Flux.just(
                new LLMCompletedEvent(conversationId, Instant.now(), iteration, response));
        log.info("========== Iteration {} ==========", iteration);
        if (stopCondition.shouldStop(response)) {
            ChatResponse completedResponse = response.toBuilder().conversationId(conversationId).build();
            Message finalMessage = Message.assistant(response.content(), response.toolCalls());
            messageStore.append(conversationId, finalMessage);
            log.info("LLM FinishReason: {}", response.finishReason());
            log.info("ToolCalls: {}", response.toolCalls());
            return Flux.concat(
                    llmCompleted,
                    Flux.just(new IterationCompletedEvent(conversationId, Instant.now(), iteration)),
                    Flux.just(new AgentCompletedEvent(conversationId, Instant.now(), completedResponse)));
        }

        Flux<AgentEvent> toolEvents = Flux.merge(
                response.toolCalls().stream().map(call -> executeTool(conversationId, call)).toList())
                .cache();
        log.info("LLM FinishReason: {}", response.finishReason());
        log.info("ToolCalls: {}", response.toolCalls());
        return Flux.concat(
                llmCompleted,
                toolEvents,
                toolEvents.ofType(ToolCompletedEvent.class)
                        .map(ToolCompletedEvent::result)
                        .collectList()
                        .flatMapMany(results -> Flux.concat(
                                Flux.just(new IterationCompletedEvent(conversationId, Instant.now(), iteration)),
                                run(nextContext(context, response, results), iteration + 1))));
    }

    private Flux<AgentEvent> executeTool(String conversationId, ToolCall call) {
        return Flux.concat(
                Flux.just(new ToolStartedEvent(conversationId, Instant.now(), call)),
                toolService.execute(call.id(), call.name(), call.arguments())
                        .map(output -> ToolResult.builder()
                                .toolCallId(call.id())
                                .toolName(call.name())
                                .output(output)
                                .build())
                        .map(result -> (AgentEvent) new ToolCompletedEvent(
                                conversationId, Instant.now(), result)));
    }

    private AgentContext nextContext(
            AgentContext context,
            ChatResponse response,
            List<ToolResult> results
    ) {
        List<Message> additions = new ArrayList<>();
        additions.add(Message.assistant(response.content(), response.toolCalls()));
        results.forEach(result -> additions.add(Message.tool(result, writeJson(result.output()))));
        messageStore.appendAll(context.conversationId(), additions);

        List<Message> messages = new ArrayList<>(context.messages());
        messages.addAll(additions);
        List<ToolCall> calls = new ArrayList<>(context.toolCalls());
        calls.addAll(response.toolCalls());
        List<ToolResult> toolResults = new ArrayList<>(context.toolResults());
        toolResults.addAll(results);

        return context.toBuilder()
                .messages(messages)
                .toolCalls(calls)
                .toolResults(toolResults)
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize tool result", exception);
        }
    }
}
