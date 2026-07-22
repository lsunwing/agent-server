package com.david.agent.tool.interceptor;

import com.david.agent.tool.ToolProperties;
import com.david.agent.tool.executor.ToolExecution;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Order(100)
@RequiredArgsConstructor
public class ToolTimeoutInterceptor implements ToolInterceptor {

    private final ToolProperties properties;

    @Override
    public Mono<Object> intercept(ToolExecution execution, ToolExecutionChain chain) {
        return chain.proceed(execution).timeout(properties.timeout());
    }
}
