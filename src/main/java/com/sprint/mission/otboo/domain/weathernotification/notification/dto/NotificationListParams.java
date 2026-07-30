package com.sprint.mission.otboo.domain.weathernotification.notification.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public record NotificationListParams(
    String cursor,
    UUID idAfter,
    @NotNull @Min(1) @Max(100) Integer limit
) {

  @AssertTrue(message = "cursor, idAfter는 함께 전달되어야 합니다")
  public boolean isCursorAndIdAfterConsistent() {
    return (cursor == null && idAfter == null) || (cursor != null && idAfter != null);
  }

  @AssertTrue(message = "cursor는 ISO-8601 형식의 시각이어야 합니다")
  public boolean isCursorParsable() {
    if (cursor == null) {
      return true;
    }
    try {
      Instant.parse(cursor);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }
}