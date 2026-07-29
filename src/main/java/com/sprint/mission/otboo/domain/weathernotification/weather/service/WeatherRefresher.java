package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherRefresher {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final KmaForecastFetcher kmaForecastFetcher;
  private final WeatherWriter weatherWriter;
  private final Clock clock;

  public List<Weather> refresh(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {
    LocalDate yesterday = LocalDate.now(clock.withZone(KST)).minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    Map<LocalDate, Weather> existingByDate = weatherRepository.findLatestRevisions(weatherGrid,
            from)
        .stream()
        .collect(Collectors.toMap(this::toForecastDate, w -> w));

    log.info("기상청 라이브 재조회: nx={}, ny={}, baseDate={}, baseTime={}", grid.nx(), grid.ny(),
        baseTime.baseDate(), baseTime.baseTime());
    List<DailyWeatherForecastDto> dailyForecasts = kmaForecastFetcher.fetch(grid, baseTime,
        clock.instant());
    List<Weather> saved = weatherWriter.save(weatherGrid, baseTime.toInstant(), dailyForecasts,
        existingByDate);
    log.info("기상청 라이브 재조회 저장 완료: nx={}, ny={}, 저장 건수={}", grid.nx(), grid.ny(), saved.size());
    return saved;
  }

  private LocalDate toForecastDate(Weather weather) {
    return weather.getForecastAt().atZone(KST).toLocalDate();
  }
}