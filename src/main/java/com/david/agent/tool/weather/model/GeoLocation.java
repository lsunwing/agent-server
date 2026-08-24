package com.david.agent.tool.weather.model;

public record GeoLocation(
        String name,
        double latitude,
        double longitude,
        String country,
        String admin1
) {
}
