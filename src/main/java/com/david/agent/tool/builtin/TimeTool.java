package com.david.agent.tool.builtin;

import com.david.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class TimeTool implements Tool {

    private static final AtomicLong LOG_SEQ = new AtomicLong(0);

    @Override
    public String name() {
        return "time";
    }

    @Override
    public String description() {
        return "仅用于用户明确询问当前时间、几点钟、现在时间等问题。不要用于天气、日期、历史事件查询。";
    }

    @Override
    public Mono<Object> execute(Map<String, Object> arguments) {
        long seq = LOG_SEQ.incrementAndGet();
        String seqTag = formatSeq(seq);

        log.info("[time-{}][1/2] execute arguments={}", seqTag, arguments);
        String now = OffsetDateTime.now().toString();
        Map<String, Object> result = Map.of("now", now);
        log.info("[time-{}][2/2] result={}", seqTag, result);
        return Mono.just(result);
    }

    private String formatSeq(long seq) {
        return String.format("%05d", seq);
    }
}
