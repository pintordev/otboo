package com.sprint.mission.otboo.batch.weatherretention.dto;

import java.time.Instant;
import java.util.UUID;

public record WeatherRetentionItem(UUID id, Instant forecastAt) {

}