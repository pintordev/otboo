package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weather.single-flight")
public record SingleFlightProperties(
    Duration lockTtl
) {

}