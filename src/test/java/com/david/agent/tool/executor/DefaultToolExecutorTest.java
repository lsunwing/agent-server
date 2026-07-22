package com.david.agent.tool.executor;

import com.david.agent.tool.Tool;
import com.david.agent.tool.ToolRegistry;
import com.david.agent.tool.interceptor.ToolExecutionChain;
import com.david.agent.tool.interceptor.ToolInterceptor;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class DefaultToolExecutorTest {

    @Test
    void appliesInterceptorsBeforeExecutingTool() {
        Tool tool = new Tool() {
            public String name() { return "test"; }
            public String description() { return "test tool"; }
            public Mono<Object> execute(Map<String, Object> arguments) { return Mono.just("done"); }
        };
        AtomicInteger intercepted = new AtomicInteger();
        ToolInterceptor interceptor = new ToolInterceptor() {
            @Override
            public Mono<Object> intercept(ToolExecution execution, ToolExecutionChain chain) {
                intercepted.incrementAndGet();
                return chain.proceed(execution);
            }
        };
        DefaultToolExecutor executor = new DefaultToolExecutor(
                new ToolRegistry(List.of(tool)), List.of(interceptor));

        StepVerifier.create(executor.execute(new ToolExecution("call", "test", Map.of())))
                .expectNext("done")
                .verifyComplete();
        org.junit.jupiter.api.Assertions.assertEquals(1, intercepted.get());
    }
}
