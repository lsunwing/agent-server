package com.david.agent.tool.finance.stock.model;

import java.time.LocalDate;

public record StockHistoryQuery(
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        int limit
) {
}
