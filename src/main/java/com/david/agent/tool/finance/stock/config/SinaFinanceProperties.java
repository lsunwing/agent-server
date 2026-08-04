package com.david.agent.tool.finance.stock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "finance.stock.sina")
public record SinaFinanceProperties(
        boolean enabled,
        String baseUrl,
        String klinePath,
        int scale,
        int maxDataLength,
        Duration timeout
) {

    public SinaFinanceProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://money.finance.sina.com.cn"
                : baseUrl;
        klinePath = klinePath == null || klinePath.isBlank()
                ? "/quotes_service/api/json_v2.php/CN_MarketData.getKLineData"
                : klinePath;
        scale = scale <= 0 ? 240 : scale;
        maxDataLength = maxDataLength <= 0 ? 200 : maxDataLength;
        timeout = timeout == null ? Duration.ofSeconds(8) : timeout;
    }
}
