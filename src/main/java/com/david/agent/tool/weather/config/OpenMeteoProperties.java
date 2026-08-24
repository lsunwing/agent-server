package com.david.agent.tool.weather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "weather.open-meteo")
public record OpenMeteoProperties(
        boolean enabled,
        String geocodingBaseUrl,
        String forecastBaseUrl,
        int forecastDays,
        Duration timeout
) {

    public OpenMeteoProperties {
        geocodingBaseUrl = geocodingBaseUrl == null || geocodingBaseUrl.isBlank()
                ? "https://geocoding-api.open-meteo.com/v1"
                : geocodingBaseUrl;
        forecastBaseUrl = forecastBaseUrl == null || forecastBaseUrl.isBlank()
                ? "https://api.open-meteo.com/v1"
                : forecastBaseUrl;
        forecastDays = forecastDays <= 0 ? 3 : Math.min(forecastDays, 16);
        timeout = timeout == null ? Duration.ofSeconds(8) : timeout;
    }
}
