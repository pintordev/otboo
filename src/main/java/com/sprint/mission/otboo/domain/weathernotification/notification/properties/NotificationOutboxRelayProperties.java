package com.sprint.mission.otboo.domain.weathernotification.notification.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.outbox-relay")
public record NotificationOutboxRelayProperties(
    @DefaultValue("100") @Positive int batchSize
) {

}