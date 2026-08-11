package com.sprint.mission.otboo.batch.weatherfetch.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.weather-change")
public record WeatherChangeProperties(
    @Positive double temperatureThreshold,
    @Positive @DecimalMax("100.0") double precipitationProbabilityThreshold,
    @Positive double precipitationAmountThreshold
) {

}