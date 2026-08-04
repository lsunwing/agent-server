package com.david.agent.tool.finance.stock.provider;

import com.david.agent.tool.finance.stock.model.StockHistory;
import com.david.agent.tool.finance.stock.model.StockHistoryQuery;
import reactor.core.publisher.Mono;

import java.util.List;

public interface FinanceProvider {

    String providerName();

    Mono<List<StockHistory>> queryHistory(StockHistoryQuery query);
}
