package com.david.agent.tool.builtin;

import com.david.agent.tool.Tool;
import com.david.agent.tool.discovery.ToolMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class WeatherTool implements Tool {

    private static final AtomicLong LOG_SEQ = new AtomicLong(0);

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String description() {
        return "查询天气。当用户询问某个城市今天、明天或未来天气情况时调用。需要城市名称，例如南京、上海、北京。";
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.local(name(), description(), List.of(
                "天气", "气温", "下雨", "降雨", "预报", "温度", "humidity", "forecast"
        ));
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "city", Map.of(
                                "type", "string",
                                "description", "城市名称，例如：北京、上海、南京"
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

        Map<String, Object> weather = mockWeather(city);
        log.info("[weather-{}][3/3] result={}", seqTag, weather);
        return Mono.just(weather);
    }

    private String normalizeCity(Object cityArg) {
        if (cityArg == null) {
            return "未知城市";
        }

        String value = String.valueOf(cityArg).trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return "未知城市";
        }
        return value;
    }

    private Map<String, Object> mockWeather(String city) {
        String key = city.toLowerCase(Locale.ROOT);

        if (key.contains("北京")) {
            return Map.of(
                    "city", city,
                    "weather", "晴",
                    "temperature", "31℃",
                    "humidity", "45%",
                    "source", "mock"
            );
        }

        if (key.contains("上海")) {
            return Map.of(
                    "city", city,
                    "weather", "多云",
                    "temperature", "33℃",
                    "humidity", "68%",
                    "source", "mock"
            );
        }

        if (key.contains("南京")) {
            return Map.of(
                    "city", city,
                    "weather", "小雨",
                    "temperature", "29℃",
                    "humidity", "78%",
                    "source", "mock"
            );
        }

        return Map.of(
                "city", city,
                "weather", "晴",
                "temperature", "30℃",
                "humidity", "60%",
                "source", "mock"
        );
    }

    private String formatSeq(long seq) {
        return String.format("%05d", seq);
    }
}
