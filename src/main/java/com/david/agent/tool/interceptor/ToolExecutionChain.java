package com.david.agent.tool.interceptor;

import com.david.agent.tool.executor.ToolExecution;
import reactor.core.publisher.Mono;

public interface ToolExecutionChain {

    Mono<Object> proceed(ToolExecution execution);
}
