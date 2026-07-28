package com.sprint.mission.otboo.external.kma;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class KmaBaseTimeCalculator {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");
  private static final Duration PUBLISH_DELAY_BUFFER = Duration.ofMinutes(20);

  private KmaBaseTimeCalculator() {
  }

  public static BaseTime calculate(Instant now) {
    ZonedDateTime target = now.minus(PUBLISH_DELAY_BUFFER).atZone(KST);
    int hour = target.getHour();

    String baseTime;
    ZonedDateTime baseDateTime = target;
    if (hour < 2) {
      baseDateTime = target.minusDays(1);
      baseTime = "2300";
    } else if (hour < 5) {
      baseTime = "0200";
    } else if (hour < 8) {
      baseTime = "0500";
    } else if (hour < 11) {
      baseTime = "0800";
    } else if (hour < 14) {
      baseTime = "1100";
    } else if (hour < 17) {
      baseTime = "1400";
    } else if (hour < 20) {
      baseTime = "1700";
    } else if (hour < 23) {
      baseTime = "2000";
    } else {
      baseTime = "2300";
    }

    String baseDate = baseDateTime.format(DATE_FORMATTER);
    return new BaseTime(baseDate, baseTime);
  }

  public record BaseTime(String baseDate, String baseTime) {

    public Instant toInstant() {
      LocalDate date = LocalDate.parse(baseDate, DATE_FORMATTER);
      LocalTime time = LocalTime.parse(baseTime, TIME_FORMATTER);
      return date.atTime(time).atZone(KST).toInstant();
    }
  }
}