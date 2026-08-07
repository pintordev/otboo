package com.sprint.mission.otboo.batch.weatherretention.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "batch.weather-retention")
public record WeatherRetentionProperties(
    @Positive int chunkSize,
    @Positive int retentionDays
) {

}