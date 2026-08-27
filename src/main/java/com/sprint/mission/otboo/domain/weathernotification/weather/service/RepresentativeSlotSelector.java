package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RepresentativeSlotSelector {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public Optional<Weather> select(List<Weather> slots, Instant referenceInstant) {
    long referenceMinutes = toMinutesOfDay(referenceInstant);
    return slots.stream()
        .min(Comparator
            .<Weather>comparingLong(w -> Math.abs(toMinutesOfDay(w.getForecastAt()) - referenceMinutes))
            .thenComparing(Weather::getForecastAt));
  }

  // 오늘 대표 슬롯 하나만 보고 stale 여부를 판단한다 - 캐시/DB 전체를 훑어 부분적으로만
  // 갱신된 미래 슬롯을 "신선함"으로 오판하지 않도록, 항상 이 기준 하나로 통일한다.
  public boolean isStale(List<Weather> slots, LocalDate today, Instant now,
      BaseTime latestBaseTime) {
    Weather todayRepresentative = select(slotsOfDate(slots, today), now).orElse(null);
    return todayRepresentative == null
        || todayRepresentative.getForecastedAt().isBefore(latestBaseTime.toInstant());
  }

  private List<Weather> slotsOfDate(List<Weather> slots, LocalDate date) {
    return slots.stream().filter(w -> toForecastDate(w).equals(date)).toList();
  }

  private LocalDate toForecastDate(Weather weather) {
    return weather.getForecastAt().atZone(KST).toLocalDate();
  }

  private long toMinutesOfDay(Instant instant) {
    ZonedDateTime kst = instant.atZone(KST);
    return kst.getHour() * 60L + kst.getMinute();
  }
}