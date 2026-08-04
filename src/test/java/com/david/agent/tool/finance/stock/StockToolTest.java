package com.david.agent.tool.finance.stock;

import com.david.agent.tool.finance.stock.provider.MockSinaFinanceProvider;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockToolTest {

    @Test
    void queriesStockHistoryByName() {
        StockService stockService = new StockService(new MockSinaFinanceProvider(), new StockCodeResolver());
        StockTool tool = new StockTool(stockService);

        StepVerifier.create(tool.execute(Map.of("code", "沃特股份", "date", "2026-07-27")))
                .assertNext(result -> {
                    Map<?, ?> payload = (Map<?, ?>) result;
                    assertEquals(true, payload.get("success"));
                    assertEquals("002886", payload.get("code"));
                    assertTrue(payload.containsKey("history"));
                })
                .verifyComplete();
    }

    @Test
    void returnsBusinessErrorWhenCodeInvalid() {
        StockService stockService = new StockService(new MockSinaFinanceProvider(), new StockCodeResolver());
        StockTool tool = new StockTool(stockService);

        StepVerifier.create(tool.execute(Map.of("code", "不存在的股票")))
                .assertNext(result -> {
                    Map<?, ?> payload = (Map<?, ?>) result;
                    assertEquals(false, payload.get("success"));
                    assertTrue(String.valueOf(payload.get("error")).contains("无法识别股票代码"));
                })
                .verifyComplete();
    }
}
