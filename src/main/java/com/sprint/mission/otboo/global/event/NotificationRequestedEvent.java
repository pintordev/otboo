package com.sprint.mission.otboo.global.event;

import java.util.Set;
import java.util.UUID;

public record NotificationRequestedEvent(
    Set<UUID> receiverIds,
    String title,
    String content,
    NotificationLevel level
) {

  public NotificationRequestedEvent {
    receiverIds = Set.copyOf(receiverIds);
  }
}