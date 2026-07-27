package com.sprint.mission.otboo.global.event;

import java.util.UUID;

public record NotificationRequestedEvent(
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {}