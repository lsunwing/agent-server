package com.david.agent.tool.weather;

import com.david.agent.tool.weather.client.OpenMeteoClient;
import com.david.agent.tool.weather.config.OpenMeteoProperties;
import com.david.agent.tool.weather.exception.WeatherApiException;
import com.david.agent.tool.weather.model.GeoLocation;
import com.david.agent.tool.weather.model.WeatherResult;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final OpenMeteoClient openMeteoClient;
    private final OpenMeteoProperties properties;

    public Mono<WeatherResult> query(String city) {
        if (!properties.enabled()) {
            return Mono.error(new WeatherApiException("天气服务未启用 (weather.open-meteo.enabled=false)"));
        }

        return openMeteoClient.searchCity(city)
                .switchIfEmpty(Mono.error(new WeatherApiException("城市检索无结果: " + city)))
                .flatMap(location -> openMeteoClient.fetchForecast(location, properties.forecastDays())
                        .map(forecast -> toResult(location, forecast)));
    }

    private WeatherResult toResult(GeoLocation location, JsonNode forecast) {
        JsonNode current = forecast.path("current");
        JsonNode daily = forecast.path("daily");

        WeatherResult.CurrentWeather currentWeather = new WeatherResult.CurrentWeather(
                describeWeatherCode(current.path("weather_code").asInt(-1)),
                formatValue(current.path("temperature_2m").asText(""), "℃"),
                formatValue(current.path("relative_humidity_2m").asText(""), "%"),
                formatValue(current.path("apparent_temperature").asText(""), "℃"),
                formatValue(current.path("wind_speed_10m").asText(""), " km/h")
        );

        List<WeatherResult.DailyForecast> dailyForecasts = new ArrayList<>();
        JsonNode dates = daily.path("time");
        for (int i = 0; i < dates.size(); i++) {
            dailyForecasts.add(new WeatherResult.DailyForecast(
                    dates.path(i).asText(""),
                    describeWeatherCode(daily.path("weather_code").path(i).asInt(-1)),
                    formatValue(daily.path("temperature_2m_max").path(i).asText(""), "℃"),
                    formatValue(daily.path("temperature_2m_min").path(i).asText(""), "℃"),
                    formatPercent(daily.path("precipitation_probability_max").path(i))
            ));
        }

        return new WeatherResult(
                true,
                location.name(),
                location.admin1(),
                location.country(),
                currentWeather,
                dailyForecasts,
                "open-meteo"
        );
    }

    private String describeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "基本晴朗";
            case 2 -> "局部多云";
            case 3 -> "阴";
            case 45, 48 -> "雾";
            case 51 -> "轻毛毛雨";
            case 53 -> "毛毛雨";
            case 55 -> "浓毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61 -> "小雨";
            case 63 -> "中雨";
            case 65 -> "大雨";
            case 66, 67 -> "冻雨";
            case 71 -> "小雪";
            case 73 -> "中雪";
            case 75 -> "大雪";
            case 77 -> "雪粒";
            case 80 -> "小阵雨";
            case 81 -> "阵雨";
            case 82 -> "强阵雨";
            case 85 -> "小阵雪";
            case 86 -> "阵雪";
            case 95 -> "雷阵雨";
            case 96, 99 -> "雷阵雨伴冰雹";
            default -> "未知";
        };
    }

    private String formatValue(String value, String unit) {
        return value == null || value.isBlank() ? "N/A" : value + unit;
    }

    private String formatPercent(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return "N/A";
        }
        return node.asText() + "%";
    }
}
