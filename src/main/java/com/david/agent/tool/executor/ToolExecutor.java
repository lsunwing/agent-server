package com.david.agent.tool.executor;

import reactor.core.publisher.Mono;

public interface ToolExecutor {

    Mono<Object> execute(ToolExecution execution);
}
