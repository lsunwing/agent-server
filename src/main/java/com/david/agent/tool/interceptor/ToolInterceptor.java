package com.david.agent.tool.interceptor;

import com.david.agent.tool.executor.ToolExecution;
import reactor.core.publisher.Mono;

public interface ToolInterceptor {

    Mono<Object> intercept(ToolExecution execution, ToolExecutionChain chain);
}
