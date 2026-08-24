package com.david.agent.tool.weather.client;

import com.david.agent.tool.weather.config.OpenMeteoProperties;
import com.david.agent.tool.weather.exception.CityNotFoundException;
import com.david.agent.tool.weather.exception.WeatherApiException;
import com.david.agent.tool.weather.model.GeoLocation;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenMeteoClient {

    private final WebClient.Builder webClientBuilder;
    private final OpenMeteoProperties properties;

    public Mono<GeoLocation> searchCity(String city) {
        String url = properties.geocodingBaseUrl() + "/search";

        return webClientBuilder.build()
                .get()
                .uri(url, uriBuilder -> uriBuilder
                        .queryParam("name", city)
                        .queryParam("count", 1)
                        .queryParam("language", "zh")
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(properties.timeout())
                .map(this::parseFirstResult)
                .onErrorMap(error -> !(error instanceof CityNotFoundException),
                        error -> new WeatherApiException("Open-Meteo 城市检索失败: " + error.getMessage(), error));
    }

    public Mono<JsonNode> fetchForecast(GeoLocation location, int days) {
        String url = properties.forecastBaseUrl() + "/forecast";

        return webClientBuilder.build()
                .get()
                .uri(url, uriBuilder -> uriBuilder
                        .queryParam("latitude", location.latitude())
                        .queryParam("longitude", location.longitude())
                        .queryParam("current", "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m")
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", days)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(properties.timeout())
                .doOnNext(body -> log.info("[open-meteo][forecast] city={} days={} received", location.name(), days))
                .onErrorMap(error -> new WeatherApiException("Open-Meteo 天气查询失败: " + error.getMessage(), error));
    }

    private GeoLocation parseFirstResult(JsonNode body) {
        JsonNode results = body.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new CityNotFoundException("未找到该城市: 请确认城市名称是否正确");
        }

        JsonNode first = results.get(0);
        return new GeoLocation(
                first.path("name").asText(""),
                first.path("latitude").asDouble(),
                first.path("longitude").asDouble(),
                first.path("country").asText(""),
                first.path("admin1").asText("")
        );
    }
}
