package com.david.agent.tool.interceptor;

import com.david.agent.tool.executor.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(0)
public class ToolLoggingInterceptor implements ToolInterceptor {

    @Override
    public Mono<Object> intercept(ToolExecution execution, ToolExecutionChain chain) {
        return chain.proceed(execution)
                .doOnSubscribe(ignored -> log.info("Executing tool: {}", execution.toolName()))
                .doOnSuccess(ignored -> log.info("Tool completed: {}", execution.toolName()))
                .doOnError(error -> log.warn("Tool failed: {}", execution.toolName(), error));
    }
}
