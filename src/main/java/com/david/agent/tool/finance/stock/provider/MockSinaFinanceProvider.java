package com.david.agent.tool.finance.stock.provider;

import com.david.agent.tool.finance.stock.model.StockHistory;
import com.david.agent.tool.finance.stock.model.StockHistoryQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnMissingBean(FinanceProvider.class)
public class MockSinaFinanceProvider implements FinanceProvider {

    private static final Map<String, String> STOCK_NAMES = new HashMap<>();

    static {
        STOCK_NAMES.put("002886", "沃特股份");
        STOCK_NAMES.put("600519", "贵州茅台");
        STOCK_NAMES.put("300750", "宁德时代");
    }

    @Override
    public String providerName() {
        return "mock-sina";
    }

    @Override
    public Mono<List<StockHistory>> queryHistory(StockHistoryQuery query) {
        LocalDate start = query.startDate();
        LocalDate end = query.endDate();
        int max = Math.max(1, query.limit());

        List<StockHistory> rows = new ArrayList<>();
        LocalDate cursor = end;
        int index = 0;

        while ((cursor.isAfter(start) || cursor.isEqual(start)) && rows.size() < max) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                rows.add(generateRow(query.code(), query.name(), cursor, index));
                index++;
            }
            cursor = cursor.minusDays(1);
        }

        return Mono.just(rows);
    }

    private StockHistory generateRow(String code, String name, LocalDate date, int index) {
        String finalName = (name == null || name.isBlank()) ? STOCK_NAMES.getOrDefault(code, "未知股票") : name;
        BigDecimal base = switch (code) {
            case "002886" -> BigDecimal.valueOf(22.35);
            case "600519" -> BigDecimal.valueOf(1540.20);
            case "300750" -> BigDecimal.valueOf(198.45);
            default -> BigDecimal.valueOf(38.60);
        };

        BigDecimal delta = BigDecimal.valueOf((index % 5 - 2) * 0.37);
        BigDecimal open = base.add(delta);
        BigDecimal close = open.add(BigDecimal.valueOf(0.42));
        BigDecimal high = close.add(BigDecimal.valueOf(0.36));
        BigDecimal low = open.subtract(BigDecimal.valueOf(0.31));
        long volume = 1_800_000L + index * 32_000L;

        return new StockHistory(code, finalName, date, open, close, high, low, volume);
    }
}
