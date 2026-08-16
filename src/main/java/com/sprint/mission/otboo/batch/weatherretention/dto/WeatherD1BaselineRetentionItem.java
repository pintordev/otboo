package com.sprint.mission.otboo.batch.weatherretention.dto;

import java.time.LocalDate;
import java.util.UUID;

public record WeatherD1BaselineRetentionItem(UUID id, LocalDate targetDate) {

}