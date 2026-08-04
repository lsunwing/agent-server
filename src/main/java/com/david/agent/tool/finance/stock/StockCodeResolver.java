package com.david.agent.tool.finance.stock;

import com.david.agent.tool.finance.exception.InvalidStockCodeException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StockCodeResolver {

    private static final Map<String, String> STOCK_NAME_MAPPING = Map.of(
            "沃特股份", "002886",
            "贵州茅台", "600519",
            "宁德时代", "300750",
            "永辉超市", "601933"
    );

    public String resolveCode(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            throw new InvalidStockCodeException("股票代码或名称不能为空");
        }

        String value = codeOrName.trim();
        if (value.matches("\\d{6}")) {
            return value;
        }

        String mapped = STOCK_NAME_MAPPING.get(value);
        if (mapped != null) {
            return mapped;
        }

        throw new InvalidStockCodeException("无法识别股票代码或名称: " + value);
    }

    public String resolveName(String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) {
            return "";
        }

        String value = codeOrName.trim();
        if (value.matches("\\d{6}")) {
            return STOCK_NAME_MAPPING.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(value))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(value);
        }

        return value;
    }
}
