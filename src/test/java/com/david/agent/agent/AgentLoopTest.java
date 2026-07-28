package com.david.agent.agent;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentCompletedEvent;
import com.david.agent.agent.event.ReasoningTimelineEvent;
import com.david.agent.agent.message.Message;
import com.david.agent.llm.LLMClient;
import com.david.agent.memory.InMemoryMessageStore;
import com.david.agent.model.ChatResponse;
import com.david.agent.model.FinishReason;
import com.david.agent.model.ToolCall;
import com.david.agent.service.ToolDiscoveryService;
import com.david.agent.service.ToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLoopTest {

    @Test
    void continuesUntilLlmReturnsWithoutTools() {
        AtomicInteger llmCalls = new AtomicInteger();
        LLMClient llmClient = context -> {
            int call = llmCalls.getAndIncrement();
            if (call < 2) {
                return Mono.just(ChatResponse.builder()
                        .finishReason(FinishReason.TOOL_CALLS)
                        .toolCalls(List.of(ToolCall.builder()
                                .id("call-" + call)
                                .name("time")
                                .arguments(Map.of())
                                .build()))
                        .build());
            }
            return Mono.just(ChatResponse.builder().content("done").build());
        };
        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(any(),
                org.mockito.ArgumentMatchers.eq("time"), org.mockito.ArgumentMatchers.eq(Map.of())))
                .thenReturn(Mono.just(Map.of("now", "test")));

        ToolDiscoveryService discoveryService = mock(ToolDiscoveryService.class);
        when(discoveryService.discoverForContext(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        AgentLoop loop = new AgentLoop(
                llmClient,
                toolService,
                discoveryService,
                new DefaultStopCondition(),
                new AgentProperties(8),
                new ObjectMapper(),
                new InMemoryMessageStore());

        StepVerifier.create(loop.stream(AgentContext.builder()
                        .conversationId("conversation-1")
                        .messages(List.of(Message.user("run")))
                        .build())
                .ofType(AgentCompletedEvent.class)
                .map(AgentCompletedEvent::response))
                .expectNextMatches(response -> "done".equals(response.content()))
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals(3, llmCalls.get());
    }

    @Test
    void emitsReadableReasoningTimelineEvents() {
        LLMClient llmClient = context -> Mono.just(ChatResponse.builder()
                .finishReason(FinishReason.TOOL_CALLS)
                .toolCalls(List.of(ToolCall.builder().id("call-1").name("weather").arguments(Map.of("city", "上海")).build()))
                .build());
        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(any(),
                org.mockito.ArgumentMatchers.eq("weather"), org.mockito.ArgumentMatchers.eq(Map.of("city", "上海"))))
                .thenReturn(Mono.just(Map.of("weather", "sunny")));

        ToolDiscoveryService discoveryService = mock(ToolDiscoveryService.class);
        when(discoveryService.discoverForContext(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        AgentLoop loop = new AgentLoop(
                llmClient,
                toolService,
                discoveryService,
                new DefaultStopCondition(),
                new AgentProperties(1),
                new ObjectMapper(),
                new InMemoryMessageStore());

        StepVerifier.create(loop.stream(AgentContext.builder()
                        .conversationId("conversation-timeline")
                        .messages(List.of(Message.user("查天气")))
                        .build())
                .ofType(ReasoningTimelineEvent.class)
                .map(ReasoningTimelineEvent::message)
                .take(3))
                .expectNext("未发现相关工具，将仅使用模型能力")
                .expectNext("Agent正在思考问题")
                .expectNext("正在调用工具获取信息")
                .verifyComplete();
    }

    @Test
    void failsWhenMaximumIterationsIsReached() {
        LLMClient llmClient = context -> Mono.just(ChatResponse.builder()
                .finishReason(FinishReason.TOOL_CALLS)
                .toolCalls(List.of(ToolCall.builder().id("call").name("time").build()))
                .build());
        ToolService toolService = mock(ToolService.class);
        when(toolService.execute(any(),
                org.mockito.ArgumentMatchers.eq("time"), org.mockito.ArgumentMatchers.eq(Map.of())))
                .thenReturn(Mono.just("ok"));

        ToolDiscoveryService discoveryService = mock(ToolDiscoveryService.class);
        when(discoveryService.discoverForContext(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        AgentLoop loop = new AgentLoop(
                llmClient,
                toolService,
                discoveryService,
                new DefaultStopCondition(),
                new AgentProperties(2),
                new ObjectMapper(),
                new InMemoryMessageStore());

        StepVerifier.create(loop.stream(AgentContext.builder()
                        .conversationId("conversation-2")
                        .messages(List.of(Message.user("run")))
                        .build()))
                .thenConsumeWhile(ignored -> true)
                .expectError(AgentLoopLimitException.class)
                .verify();
    }
}
