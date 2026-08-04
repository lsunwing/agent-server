package com.david.agent.tool.finance.stock.model;

import java.util.List;

public record StockHistoryResult(
        boolean success,
        String code,
        String name,
        String message,
        List<StockHistory> history
) {
    public StockHistoryResult {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
