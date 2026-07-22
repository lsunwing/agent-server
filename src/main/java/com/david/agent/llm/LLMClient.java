package com.david.agent.llm;

import com.david.agent.agent.context.AgentContext;
import com.david.agent.model.ChatResponse;
import reactor.core.publisher.Mono;

public interface LLMClient {

    Mono<ChatResponse> chat(AgentContext context);
}
