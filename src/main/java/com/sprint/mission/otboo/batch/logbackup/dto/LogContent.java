package com.sprint.mission.otboo.batch.logbackup.dto;

import java.time.LocalDate;

public record LogContent(String groupLabel, LocalDate date, byte[] lines, int pageNumber) {

  public LogContent {
    lines = lines == null ? null : lines.clone();
  }

  @Override
  public byte[] lines() {
    return lines == null ? null : lines.clone();
  }
}
