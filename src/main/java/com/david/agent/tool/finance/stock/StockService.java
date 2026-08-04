package com.david.agent.tool.finance.stock;

import com.david.agent.tool.finance.exception.FinanceApiException;
import com.david.agent.tool.finance.exception.StockNotFoundException;
import com.david.agent.tool.finance.stock.model.StockHistory;
import com.david.agent.tool.finance.stock.model.StockHistoryQuery;
import com.david.agent.tool.finance.stock.model.StockHistoryResult;
import com.david.agent.tool.finance.stock.provider.FinanceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final FinanceProvider financeProvider;
    private final StockCodeResolver stockCodeResolver;

    public Mono<StockHistoryResult> queryHistory(String codeOrName, LocalDate startDate, LocalDate endDate, int limit) {
        String code = stockCodeResolver.resolveCode(codeOrName);
        String name = stockCodeResolver.resolveName(codeOrName);

        LocalDate end = endDate == null ? LocalDate.now().minusDays(1) : endDate;
        LocalDate start = startDate == null ? end.minusDays(7) : startDate;
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        StockHistoryQuery query = new StockHistoryQuery(code, name, start, end, Math.max(1, limit));

        return financeProvider.queryHistory(query)
                .map(history -> toResult(code, name, history))
                .onErrorMap(error -> {
                    if (error instanceof StockNotFoundException) {
                        return error;
                    }
                    return new FinanceApiException("财经接口调用失败: " + error.getMessage(), error);
                });
    }

    private StockHistoryResult toResult(String code, String name, List<StockHistory> history) {
        if (history == null || history.isEmpty()) {
            throw new StockNotFoundException("未查询到股票历史行情: " + code);
        }

        List<StockHistory> sorted = history.stream()
                .sorted(Comparator.comparing(StockHistory::date).reversed())
                .toList();

        return new StockHistoryResult(
                true,
                code,
                name,
                "ok",
                sorted
        );
    }
}
