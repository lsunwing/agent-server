package com.david.agent.tool.weather.model;

import java.util.List;

public record WeatherResult(
        boolean success,
        String city,
        String region,
        String country,
        CurrentWeather current,
        List<DailyForecast> daily,
        String source
) {

    public WeatherResult {
        daily = daily == null ? List.of() : List.copyOf(daily);
    }

    public record CurrentWeather(
            String weather,
            String temperature,
            String humidity,
            String apparentTemperature,
            String windSpeed
    ) {
    }

    public record DailyForecast(
            String date,
            String weather,
            String tempMax,
            String tempMin,
            String precipitationProbability
    ) {
    }
}
