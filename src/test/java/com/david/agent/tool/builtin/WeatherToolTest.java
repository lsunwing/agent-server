package com.david.agent.tool.builtin;

import com.david.agent.tool.weather.WeatherService;
import com.david.agent.tool.weather.model.WeatherResult;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeatherToolTest {

    @Test
    void providesWeatherDataForCity() {
        WeatherService weatherService = mock(WeatherService.class);
        when(weatherService.query(anyString())).thenReturn(Mono.just(new WeatherResult(
                true, "上海", "上海", "中国",
                new WeatherResult.CurrentWeather("晴", "28", "60%", "30", "12"),
                java.util.List.of(),
                "mock"
        )));
        WeatherTool tool = new WeatherTool(weatherService);

        assertEquals("weather", tool.name());
        assertTrue(tool.description().contains("天气"));
        assertEquals("object", tool.inputSchema().get("type"));

        StepVerifier.create(tool.execute(Map.of("city", "上海")))
                .expectNextMatches(result -> {
                    WeatherResult weatherResult = (WeatherResult) result;
                    return "上海".equals(weatherResult.city())
                            && "晴".equals(weatherResult.current().weather())
                            && "28".equals(weatherResult.current().temperature())
                            && "60%".equals(weatherResult.current().humidity());
                })
                .verifyComplete();
    }
}
