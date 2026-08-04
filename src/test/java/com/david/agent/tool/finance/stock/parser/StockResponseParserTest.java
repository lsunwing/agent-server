package com.david.agent.tool.finance.stock.parser;

import com.david.agent.tool.finance.stock.model.StockHistory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockResponseParserTest {

    @Test
    void parsesSinaKLineRows() {
        StockResponseParser parser = new StockResponseParser(new ObjectMapper());

        String raw = """
                [
                  {"day":"2026-07-28","open":"22.10","high":"22.50","low":"21.90","close":"22.30","volume":"123456"},
                  {"day":"2026-07-27","open":"22.00","high":"22.40","low":"21.80","close":"22.20","volume":"100001"}
                ]
                """;

        List<StockHistory> rows = parser.parseSinaKLine("002886", "沃特股份", raw);

        assertEquals(2, rows.size());
        assertEquals("002886", rows.get(0).code());
        assertEquals("沃特股份", rows.get(0).name());
        assertEquals("22.30", rows.get(0).close().toString());
    }
}
