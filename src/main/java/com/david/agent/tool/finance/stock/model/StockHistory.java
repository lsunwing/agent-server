package com.david.agent.tool.finance.stock.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockHistory(
        String code,
        String name,
        LocalDate date,
        BigDecimal open,
        BigDecimal close,
        BigDecimal high,
        BigDecimal low,
        Long volume
) {
}
