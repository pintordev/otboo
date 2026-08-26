package com.sprint.mission.otboo.external.kma;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weather.kma.timeout")
public record KmaFeignProperties(
    @NotNull @DurationMin(millis = 1) Duration connect,
    @NotNull @DurationMin(millis = 1) Duration read
) {

}