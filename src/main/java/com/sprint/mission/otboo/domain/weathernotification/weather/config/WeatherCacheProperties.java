package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weather.cache")
public record WeatherCacheProperties(
    @Positive int ttlHours,
    @Positive int locationTtlDays
) {

}