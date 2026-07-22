package com.david.agent.agent;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.agent.runtime.AgentRuntime;
import com.david.agent.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AgentExecutor {

    private final AgentRuntime runtime;

    public Mono<ChatResponse> execute(AgentContext context) {
        return runtime.execute(context);
    }

    public Flux<AgentEvent> stream(AgentContext context) {
        return runtime.stream(context);
    }
}
