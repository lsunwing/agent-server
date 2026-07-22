package com.david.agent.agent.runtime;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.agent.event.AgentEvent;
import com.david.agent.model.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentRuntime {

    Mono<ChatResponse> execute(AgentContext context);

    Flux<AgentEvent> stream(AgentContext context);
}
