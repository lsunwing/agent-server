package com.david.agent.tool.finance.stock.provider;

import com.david.agent.tool.finance.stock.client.SinaFinanceClient;
import com.david.agent.tool.finance.stock.model.StockHistory;
import com.david.agent.tool.finance.stock.model.StockHistoryQuery;
import com.david.agent.tool.finance.stock.parser.StockResponseParser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Primary
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "finance.stock.sina", name = "enabled", havingValue = "true")
public class SinaFinanceProvider implements FinanceProvider {

    private final SinaFinanceClient sinaFinanceClient;
    private final StockResponseParser stockResponseParser;

    @Override
    public String providerName() {
        return "sina";
    }

    @Override
    public Mono<List<StockHistory>> queryHistory(StockHistoryQuery query) {
        String symbol = toSinaSymbol(query.code());
        int dataLength = Math.max(100, query.limit() * 5);

        return sinaFinanceClient.queryKLine(symbol, dataLength)
                .map(raw -> stockResponseParser.parseSinaKLine(query.code(), query.name(), raw))
                .map(rows -> rows.stream()
                        .filter(row -> isWithinRange(row.date(), query.startDate(), query.endDate()))
                        .sorted(Comparator.comparing(StockHistory::date).reversed())
                        .limit(query.limit())
                        .toList());
    }

    private boolean isWithinRange(LocalDate value, LocalDate start, LocalDate end) {
        if (value == null || value.equals(LocalDate.MIN)) {
            return false;
        }
        return (value.isAfter(start) || value.isEqual(start))
                && (value.isBefore(end) || value.isEqual(end))
                && value.getDayOfWeek() != DayOfWeek.SATURDAY
                && value.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private String toSinaSymbol(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        if (code.startsWith("6") || code.startsWith("9")) {
            return "sh" + code;
        }
        if (code.startsWith("0") || code.startsWith("2") || code.startsWith("3")) {
            return "sz" + code;
        }
        if (code.startsWith("4") || code.startsWith("8")) {
            return "bj" + code;
        }
        return "sz" + code;
    }
}
