package com.david.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@Component
public class TimeTool implements Tool {

    @Override
    public String name() {
        return "time";
    }

    @Override
    public String description() {
        return "Returns the server's current date and time with timezone.";
    }

    @Override
    public Mono<Object> execute(Map<String, Object> arguments) {
        log.info("TimeTool execute arguments: {}", arguments);
        return Mono.just(Map.of("now", OffsetDateTime.now().toString()));
    }
}
