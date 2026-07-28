package com.david.agent.tool.discovery;

import com.david.agent.tool.ToolDefinition;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleKeywordRetrieverTest {

    @Test
    void matchesWeatherByKeyword() {
        SimpleKeywordRetriever retriever = new SimpleKeywordRetriever();

        ToolDescriptor weather = new ToolDescriptor(
                new ToolDefinition("weather", "查询天气", Map.of()),
                ToolMetadata.local("weather", "查询天气", List.of("天气", "气温", "预报"))
        );
        ToolDescriptor time = new ToolDescriptor(
                new ToolDefinition("time", "查询时间", Map.of()),
                ToolMetadata.local("time", "查询时间", List.of("时间", "几点"))
        );

        StepVerifier.create(retriever.retrieve("帮我看看上海天气", List.of(time, weather), Map.of()))
                .assertNext(result -> {
                    assertEquals(1, result.size());
                    assertEquals("weather", result.get(0).name());
                })
                .verifyComplete();
    }
}
