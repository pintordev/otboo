package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.dto.LocationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.exception.InvalidCoordinateException;
import com.sprint.mission.otboo.domain.weathernotification.weather.mapper.WeatherMapper;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter;
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
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class WeatherService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final KmaForecastFetcher kmaForecastFetcher;
  private final WeatherWriter weatherWriter;
  private final LocationResolver locationResolver;
  private final WeatherMapper weatherMapper;
  private final Clock clock;

  public List<WeatherDto> getWeather(double latitude, double longitude) {
    KmaGridPoint grid = toGrid(latitude, longitude);
    log.debug("날씨 조회 요청: nx={}, ny={}", grid.nx(), grid.ny());
    WeatherGrid weatherGrid = locationResolver.resolveWeatherGrid(grid);

    LocalDate today = LocalDate.now(clock.withZone(KST));
    LocalDate yesterday = today.minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    List<Weather> latestRevisions = weatherRepository.findLatestRevisions(weatherGrid, from);

    Map<LocalDate, Weather> existingByDate = latestRevisions.stream()
        .collect(Collectors.toMap(this::toForecastDate, w -> w));

    BaseTime latestBaseTime = KmaBaseTimeCalculator.calculate(clock.instant());
    Weather todayWeather = existingByDate.get(today);
    boolean stale = todayWeather == null
        || todayWeather.getForecastedAt().isBefore(latestBaseTime.toInstant());

    List<Weather> result = stale
        ? fetchLiveAndSave(weatherGrid, grid, latestBaseTime, existingByDate)
        : latestRevisions.stream()
            .filter(w -> !toForecastDate(w).isBefore(today))
            .toList();

    List<String> locationNames = locationResolver.resolveLocationNames(latitude, longitude);
    return result.stream()
        .map(weather -> weatherMapper.toDto(weather, weatherGrid, latitude, longitude,
            locationNames))
        .toList();
  }

  public LocationDto getLocation(double latitude, double longitude) {
    KmaGridPoint grid = toGrid(latitude, longitude);
    WeatherGrid weatherGrid = locationResolver.resolveWeatherGrid(grid);
    List<String> locationNames = locationResolver.resolveLocationNames(latitude, longitude);
    return new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
        locationNames);
  }

  private LocalDate toForecastDate(Weather weather) {
    return weather.getForecastAt().atZone(KST).toLocalDate();
  }

  private KmaGridPoint toGrid(double latitude, double longitude) {
    try {
      return KmaGridConverter.toGrid(latitude, longitude);
    } catch (IllegalArgumentException e) {
      log.warn("한반도 범위를 벗어난 좌표 요청");
      throw InvalidCoordinateException.of(latitude, longitude);
    }
  }

  private List<Weather> fetchLiveAndSave(WeatherGrid weatherGrid, KmaGridPoint grid,
      BaseTime baseTime, Map<LocalDate, Weather> existingByDate) {
    log.info("기상청 라이브 재조회: nx={}, ny={}, baseDate={}, baseTime={}", grid.nx(), grid.ny(),
        baseTime.baseDate(), baseTime.baseTime());
    List<DailyWeatherForecastDto> dailyForecasts = kmaForecastFetcher.fetch(grid, baseTime,
        clock.instant());
    List<Weather> saved = weatherWriter.save(weatherGrid, baseTime.toInstant(), dailyForecasts,
        existingByDate);
    log.info("기상청 라이브 재조회 저장 완료: nx={}, ny={}, 저장 건수={}", grid.nx(), grid.ny(), saved.size());
    return saved;
  }
}