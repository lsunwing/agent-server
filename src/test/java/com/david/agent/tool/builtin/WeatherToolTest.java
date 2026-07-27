package com.david.agent.tool.builtin;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherToolTest {

    @Test
    void providesWeatherDataForCity() {
        WeatherTool tool = new WeatherTool();

        assertEquals("weather", tool.name());
        assertTrue(tool.description().contains("查询天气"));
        assertEquals("object", tool.inputSchema().get("type"));

        StepVerifier.create(tool.execute(Map.of("city", "上海")))
                .expectNextMatches(result -> {
                    Map<?, ?> map = (Map<?, ?>) result;
                    return "上海".equals(map.get("city"))
                            && map.containsKey("weather")
                            && map.containsKey("temperature")
                            && map.containsKey("humidity");
                })
                .verifyComplete();
    }
}
