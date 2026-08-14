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
import com.sprint.mission.otboo.external.kma.KmaGridConverter;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class WeatherService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final WeatherRefresher weatherRefresher;
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

    BaseTime latestBaseTime = KmaBaseTimeCalculator.calculate(clock.instant());
    Weather todayWeather = latestRevisions.stream()
        .filter(w -> toForecastDate(w).equals(today))
        .findFirst()
        .orElse(null);
    boolean stale = todayWeather == null
        || todayWeather.getForecastedAt().isBefore(latestBaseTime.toInstant());

    List<Weather> fetched = stale
        ? weatherRefresher.refresh(weatherGrid, grid, latestBaseTime)
        : latestRevisions;

    List<Weather> result = fetched.stream()
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
}