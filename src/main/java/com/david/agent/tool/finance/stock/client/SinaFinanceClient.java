package com.david.agent.tool.finance.stock.client;

import com.david.agent.tool.finance.exception.FinanceApiException;
import com.david.agent.tool.finance.stock.config.SinaFinanceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Component
@RequiredArgsConstructor
public class SinaFinanceClient {

    private final WebClient.Builder webClientBuilder;
    private final SinaFinanceProperties properties;

    public Mono<String> queryKLine(String symbol, int dataLength) {
        int finalLength = Math.min(Math.max(1, dataLength), properties.maxDataLength());

        URI requestUri = UriComponentsBuilder.fromHttpUrl(properties.baseUrl())
                .path(properties.klinePath())
                .queryParam("symbol", symbol)
                .queryParam("scale", properties.scale())
                .queryParam("ma", "no")
                .queryParam("datalen", finalLength)
                .build(true)
                .toUri();

        log.info("[sina][kline][request] url={}", requestUri);

        WebClient client = webClientBuilder.baseUrl(properties.baseUrl()).build();
        return client.get()
                .uri(requestUri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(properties.timeout())
                .doOnNext(body -> log.info("[sina][kline][response] symbol={} length={} bodySize={}", symbol, finalLength, body.length()))
                .onErrorMap(error -> new FinanceApiException("调用新浪财经接口失败: " + error.getMessage(), error));
    }
}
