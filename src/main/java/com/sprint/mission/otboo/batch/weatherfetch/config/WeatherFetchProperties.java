package com.sprint.mission.otboo.batch.weatherfetch.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "batch.weather-fetch")
public record WeatherFetchProperties(
    @Positive int chunkSize,
    @Positive int skipLimit,
    @Positive int retryLimit
) {

}