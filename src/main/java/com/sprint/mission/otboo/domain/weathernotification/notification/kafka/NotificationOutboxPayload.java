package com.sprint.mission.otboo.domain.weathernotification.notification.kafka;

import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.UUID;

public record NotificationOutboxPayload(
    UUID eventId,
    NotificationRequestedEvent event
) {

}