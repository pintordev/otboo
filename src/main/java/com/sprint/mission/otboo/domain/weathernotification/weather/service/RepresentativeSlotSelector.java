package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import java.time.Instant;
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
        .min(Comparator.comparingLong(
            w -> Math.abs(toMinutesOfDay(w.getForecastAt()) - referenceMinutes)));
  }

  private long toMinutesOfDay(Instant instant) {
    ZonedDateTime kst = instant.atZone(KST);
    return kst.getHour() * 60L + kst.getMinute();
  }
}