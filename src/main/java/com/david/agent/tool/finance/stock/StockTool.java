package com.david.agent.tool.finance.stock;

import com.david.agent.tool.Tool;
import com.david.agent.tool.discovery.ToolMetadata;
import com.david.agent.tool.finance.stock.model.StockHistoryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StockTool implements Tool {

    private final StockService stockService;

    @Override
    public String name() {
        return "stock_history";
    }

    @Override
    public String description() {
        return "查询股票历史行情数据。当用户询问股票昨日收盘价、最近走势、历史K线时调用。支持股票名称或6位代码。返回开盘、收盘、最高、最低、成交量。";
    }

    @Override
    public ToolMetadata metadata() {
        return ToolMetadata.remoteApi(name(), description()).withKeywords(List.of(
                "股票", "收盘", "开盘", "行情", "走势", "k线", "股价", "证券", "finance", "stock"
        ));
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "code", Map.of("type", "string", "description", "股票代码或名称，例如600519或贵州茅台"),
                        "name", Map.of("type", "string", "description", "股票名称，可与code二选一"),
                        "date", Map.of("type", "string", "description", "单日查询日期，例如2026-07-28"),
                        "startDate", Map.of("type", "string", "description", "起始日期，例如2026-07-20"),
                        "endDate", Map.of("type", "string", "description", "结束日期，例如2026-07-28"),
                        "limit", Map.of("type", "integer", "description", "返回条数，默认5")
                ),
                "required", List.of()
        );
    }

    @Override
    public Mono<Object> execute(Map<String, Object> args) {
        String code = readString(args, "code");
        String name = readString(args, "name");
        String key = !code.isBlank() ? code : name;

        if (key.isBlank()) {
            key = "";
        }

        LocalDate date = readDate(args, "date");
        LocalDate startDate = readDate(args, "startDate");
        LocalDate endDate = readDate(args, "endDate");
        int limit = readInt(args, "limit", 5);

        if (date != null) {
            startDate = date;
            endDate = date;
            limit = 1;
        }

        return stockService.queryHistory(key, startDate, endDate, limit)
                .map(this::toToolPayload)
                .cast(Object.class)
                .onErrorResume(error -> Mono.just(Map.of(
                        "success", false,
                        "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()
                )));
    }

    private Map<String, Object> toToolPayload(StockHistoryResult result) {
        return Map.of(
                "success", result.success(),
                "code", result.code(),
                "name", result.name(),
                "message", result.message(),
                "history", result.history()
        );
    }

    private String readString(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private LocalDate readDate(Map<String, Object> args, String key) {
        String value = readString(args, key);
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("日期格式错误(" + key + "): " + value + "，应为yyyy-MM-dd");
        }
    }

    private int readInt(Map<String, Object> args, String key, int defaultValue) {
        if (args == null || args.get(key) == null) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
