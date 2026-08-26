package com.sprint.mission.otboo.external.kma;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weather.kma.timeout")
public record KmaFeignProperties(
    Duration connect,
    Duration read
) {

}