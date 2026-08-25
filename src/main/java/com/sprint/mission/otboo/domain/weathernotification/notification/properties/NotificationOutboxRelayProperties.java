package com.sprint.mission.otboo.domain.weathernotification.notification.properties;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.outbox-relay")
public record NotificationOutboxRelayProperties(
    // relay()가 batchSize × Kafka 전송 timeout(5초)만큼 락+트랜잭션을 점유할 수 있어(동기 발행),
    // NotificationOutboxRelayScheduler의 lockAtMostFor 기본값(PT10M)보다 충분히 작게 유지한다.
    @DefaultValue("20") @Positive int batchSize
) {

}