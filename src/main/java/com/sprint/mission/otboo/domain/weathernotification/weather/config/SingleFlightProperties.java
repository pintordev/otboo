package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weather.single-flight")
public record SingleFlightProperties(
    @NotNull @DurationMin(millis = 1) Duration lockTtl
) {

}