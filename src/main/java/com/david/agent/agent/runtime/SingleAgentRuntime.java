package com.david.agent.agent.runtime;

import com.david.agent.agent.AgentLoop;
import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentCompletedEvent;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SingleAgentRuntime implements AgentRuntime {

    private final AgentLoop agentLoop;

    @Override
    public Mono<ChatResponse> execute(AgentContext context) {
        return stream(context)
                .ofType(AgentCompletedEvent.class)
                .next()
                .map(AgentCompletedEvent::response);
    }

    @Override
    public Flux<AgentEvent> stream(AgentContext context) {
        return agentLoop.stream(context);
    }
}
