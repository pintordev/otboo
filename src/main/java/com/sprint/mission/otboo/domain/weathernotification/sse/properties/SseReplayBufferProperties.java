package com.sprint.mission.otboo.domain.weathernotification.sse.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sse.replay-buffer")
public record SseReplayBufferProperties(
    @DefaultValue("10") @Positive int retentionMinutes
) {

}