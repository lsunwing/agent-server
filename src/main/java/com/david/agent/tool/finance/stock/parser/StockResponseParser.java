package com.david.agent.tool.finance.stock.parser;

import com.david.agent.tool.finance.exception.FinanceApiException;
import com.david.agent.tool.finance.stock.model.StockHistory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StockResponseParser {

    private static final TypeReference<List<Map<String, String>>> LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public List<StockHistory> parseSinaKLine(String code, String name, String rawBody) {
        if (rawBody == null || rawBody.isBlank() || "null".equalsIgnoreCase(rawBody.trim())) {
            return List.of();
        }

        try {
            List<Map<String, String>> rows = objectMapper.readValue(rawBody, LIST_TYPE);
            List<StockHistory> result = new ArrayList<>();
            for (Map<String, String> row : rows) {
                LocalDate date = parseDate(row.get("day"));
                BigDecimal open = parseDecimal(row.get("open"));
                BigDecimal close = parseDecimal(row.get("close"));
                BigDecimal high = parseDecimal(row.get("high"));
                BigDecimal low = parseDecimal(row.get("low"));
                Long volume = parseLong(row.get("volume"));

                result.add(new StockHistory(code, name, date, open, close, high, low, volume));
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new FinanceApiException("解析新浪财经K线数据失败: " + exception.getMessage(), exception);
        }
    }

    private LocalDate parseDate(String day) {
        if (day == null || day.isBlank()) {
            return LocalDate.MIN;
        }
        String normalized = day.trim();
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        return LocalDate.parse(normalized);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return new BigDecimal(value.trim()).longValue();
    }
}
