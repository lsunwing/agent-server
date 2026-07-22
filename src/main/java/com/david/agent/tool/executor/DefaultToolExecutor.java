package com.david.agent.tool.executor;

import com.david.agent.tool.ToolRegistry;
import com.david.agent.tool.interceptor.ToolExecutionChain;
import com.david.agent.tool.interceptor.ToolInterceptor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class DefaultToolExecutor implements ToolExecutor {

    private final ToolExecutionChain chain;

    public DefaultToolExecutor(ToolRegistry registry, List<ToolInterceptor> interceptors) {
        ToolExecutionChain terminal = execution -> registry.getRequired(execution.toolName())
                .execute(execution.arguments());
        ToolExecutionChain current = terminal;
        for (int index = interceptors.size() - 1; index >= 0; index--) {
            ToolInterceptor interceptor = interceptors.get(index);
            ToolExecutionChain next = current;
            current = execution -> interceptor.intercept(execution, next);
        }
        this.chain = current;
    }

    @Override
    public Mono<Object> execute(ToolExecution execution) {
        return Mono.defer(() -> chain.proceed(execution));
    }
}
