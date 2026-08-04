package com.david.agent.tool.discovery;

import com.david.agent.tool.ToolDefinition;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinanceKeywordRoutingTest {

    @Test
    void prefersStockToolForStockQuestion() {
        SimpleKeywordRetriever retriever = new SimpleKeywordRetriever();

        ToolDescriptor stock = new ToolDescriptor(
                new ToolDefinition("stock_history", "查询股票历史行情", Map.of()),
                ToolMetadata.remoteApi("stock_history", "查询股票历史行情").withKeywords(List.of("股票", "收盘", "走势", "行情"))
        );
        ToolDescriptor weather = new ToolDescriptor(
                new ToolDefinition("weather", "查询天气", Map.of()),
                ToolMetadata.local("weather", "查询天气", List.of("天气", "气温"))
        );

        StepVerifier.create(retriever.retrieve("沃特股份昨天收盘价是多少", List.of(weather, stock), Map.of()))
                .assertNext(result -> {
                    assertEquals("stock_history", result.get(0).name());
                })
                .verifyComplete();
    }
}
