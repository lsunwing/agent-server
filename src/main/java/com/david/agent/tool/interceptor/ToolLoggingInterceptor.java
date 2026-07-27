package com.david.agent.tool.interceptor;

import com.david.agent.tool.executor.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Order(0)
public class ToolLoggingInterceptor implements ToolInterceptor {

    private static final AtomicLong EXECUTION_SEQ = new AtomicLong(0);

    @Override
    public Mono<Object> intercept(ToolExecution execution, ToolExecutionChain chain) {
        long seq = EXECUTION_SEQ.incrementAndGet();
        String seqTag = formatSeq(seq);
        long startNanos = System.nanoTime();

        return chain.proceed(execution)
                .doOnSubscribe(ignored -> log.info(
                        "[tool-{}][1/4] start tool={} callId={} args={}",
                        seqTag,
                        execution.toolName(),
                        valueOrDash(execution.toolCallId()),
                        preview(execution.arguments())
                ))
                .doOnNext(result -> log.info(
                        "[tool-{}][2/4] result tool={} preview={}",
                        seqTag,
                        execution.toolName(),
                        preview(result)
                ))
                .doOnSuccess(ignored -> log.info(
                        "[tool-{}][3/4] success tool={} elapsedMs={}",
                        seqTag,
                        execution.toolName(),
                        elapsedMs(startNanos)
                ))
                .doOnError(error -> log.warn(
                        "[tool-{}][3/4] failed tool={} elapsedMs={} errorType={} message={}",
                        seqTag,
                        execution.toolName(),
                        elapsedMs(startNanos),
                        error.getClass().getSimpleName(),
                        valueOrDash(error.getMessage()),
                        error
                ))
                .doFinally(signalType -> log.info(
                        "[tool-{}][4/4] finish tool={} signal={}",
                        seqTag,
                        execution.toolName(),
                        signalType
                ));
    }

    private static String formatSeq(long seq) {
        return String.format("%06d", seq);
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String valueOrDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private static String preview(Object value) {
        if (value == null) {
            return "null";
        }

        String text = String.valueOf(value);
        int maxLen = 240;
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...(truncated)";
    }
}
