package com.sprint.mission.otboo.domain.weathernotification.notification.dto;

import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {

}