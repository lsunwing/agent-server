package com.david.agent.tool.builtin;

import com.david.agent.tool.Tool;
import com.david.agent.tool.discovery.ToolMetadata;
import com.david.agent.tool.weather.WeatherService;
import com.david.agent.tool.weather.model.WeatherResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTool implements Tool {

    private static final AtomicLong LOG_SEQ = new AtomicLong(0);

    private final WeatherService weatherService;

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询实时天气和未来几天预报。当用户询问某个城市今天、明天或未来天气情况时调用。需要城市名称，支持国内外城市，例如南京、上海、北京、Tokyo。";
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.remoteApi(name(), description()).withKeywords(List.of(
                "天气", "气温", "下雨", "降雨", "预报", "温度", "humidity", "forecast", "weather"
        ));
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "城市名称，例如：北京、上海、南京、Tokyo"
                        )
                ),
                "required", java.util.List.of("city")
        );
    }

    @Override
    public Mono<Object> execute(Map<String, Object> arguments) {
        long seq = LOG_SEQ.incrementAndGet();
        String seqTag = formatSeq(seq);

        log.info("[weather-{}][1/3] rawArguments={}", seqTag, arguments);

        String city = normalizeCity(arguments.get("city"));
        log.info("[weather-{}][2/3] normalizedCity={}", seqTag, city);

        return weatherService.query(city)
                .cast(Object.class)
                .doOnNext(result -> log.info("[weather-{}][3/3] result={}", seqTag, ((WeatherResult) result).current()))
                .onErrorMap(error -> {
                    log.warn("[weather-{}][error] city={} message={}", seqTag, city, error.getMessage());
                    return error;
                });
    }

    private String normalizeCity(Object cityArg) {
        if (cityArg == null) {
            return "";
        }

        String value = String.valueOf(cityArg).trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value;
    }

    private String formatSeq(long seq) {
        return String.format("%05d", seq);
    }
}
