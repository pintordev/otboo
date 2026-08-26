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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

// 클래스 레벨 @Transactional(readOnly = true)를 두지 않는다 - WeatherRefresher와 동일한 이유로,
// getWeather()가 stale일 때 refreshSlots()(Feign 호출)를 그대로 감싸면 그 대기 시간 내내
// DB 커넥션을 쥐고 있게 된다. 쓰기가 필요한 지점(WeatherWriter.saveSlots())은 이미 자체
// REQUIRES_NEW를 갖고 있고, 읽기 쿼리는 repository 메서드 자체가 개별 처리한다.
@Slf4j
@RequiredArgsConstructor
@Service
public class WeatherService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  // D1 이후(내일부터) 날짜의 대표 슬롯 기준 시각(KST) - referenceInstant()에서 사용
  private static final int FUTURE_REPRESENTATIVE_HOUR = 15;

  private final WeatherRepository weatherRepository;
  private final WeatherRefresher weatherRefresher;
  private final LocationResolver locationResolver;
  private final WeatherMapper weatherMapper;
  private final RepresentativeSlotSelector representativeSlotSelector;
  private final Clock clock;
  private final WeatherCacheProvider weatherCacheProvider;
  @Qualifier("weatherRefreshExecutor")
  private final Executor weatherRefreshExecutor;
  @Qualifier("kakaoLocationExecutor")
  private final Executor kakaoLocationExecutor;

  // 결정 1의 3단(캐시→DB→재조회) 조회 - 캐시/위치 어느 쪽이 느려도 Tomcat 워커 스레드를 블로킹하지 않는다.
  public CompletableFuture<List<WeatherDto>> getWeatherAsync(double latitude, double longitude) {
    KmaGridPoint grid = toGrid(latitude, longitude);
    WeatherGrid weatherGrid = locationResolver.resolveWeatherGrid(grid);
    LocalDate today = LocalDate.now(clock.withZone(KST));
    LocalDate yesterday = today.minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    BaseTime latestBaseTime = KmaBaseTimeCalculator.calculate(clock.instant());

    List<Weather> cachedSlots = weatherCacheProvider.findCachedSlots(weatherGrid);
    CompletableFuture<List<Weather>> weatherFuture = isStale(cachedSlots, today, latestBaseTime)
        ? fetchFreshAsync(weatherGrid, grid, latestBaseTime, from, today)
        : CompletableFuture.completedFuture(cachedSlots);

    CompletableFuture<List<String>> locationFuture = locationResolver
        .resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor)
        .orTimeout(5, TimeUnit.SECONDS);

    return weatherFuture.thenCombine(locationFuture, (slots, locationNames) ->
            representativesFrom(slots, today).stream()
                .map(w -> weatherMapper.toDto(w, weatherGrid, latitude, longitude, locationNames))
                .toList())
        .exceptionally(ex -> {
          log.warn("날씨/위치 조회 타임아웃 또는 실패, DB 값으로 폴백", ex);
          List<Weather> fallbackSlots = weatherRepository
              .findAllByWeatherGridAndForecastAtGreaterThanEqual(weatherGrid, from);
          List<String> fallbackNames = List.of();
          return representativesFrom(fallbackSlots, today).stream()
              .map(w -> weatherMapper.toDto(w, weatherGrid, latitude, longitude, fallbackNames))
              .toList();
        });
  }

  private CompletableFuture<List<Weather>> fetchFreshAsync(WeatherGrid weatherGrid,
      KmaGridPoint grid, BaseTime latestBaseTime, Instant from, LocalDate today) {
    List<Weather> dbSlots = weatherRepository
        .findAllByWeatherGridAndForecastAtGreaterThanEqual(weatherGrid, from);
    if (!isStale(dbSlots, today, latestBaseTime)) {
      weatherCacheProvider.putSlots(weatherGrid, dbSlots);
      return CompletableFuture.completedFuture(dbSlots);
    }
    return weatherRefresher
        .refreshSlotsAsync(weatherGrid, grid, latestBaseTime, dbSlots, weatherRefreshExecutor)
        .orTimeout(5, TimeUnit.SECONDS);
  }

  // 기존 동기 getWeather()의 stale 판정과 완전히 동일 - 오늘 대표 슬롯 하나만 본다.
  private boolean isStale(List<Weather> candidateSlots, LocalDate today, BaseTime latestBaseTime) {
    Weather todayRepresentative = representativeSlotSelector
        .select(slotsOfDate(candidateSlots, today), clock.instant())
        .orElse(null);
    return todayRepresentative == null
        || todayRepresentative.getForecastedAt().isBefore(latestBaseTime.toInstant());
  }

  public List<WeatherDto> getWeather(double latitude, double longitude) {
    KmaGridPoint grid = toGrid(latitude, longitude);
    log.debug("날씨 조회 요청: nx={}, ny={}", grid.nx(), grid.ny());
    WeatherGrid weatherGrid = locationResolver.resolveWeatherGrid(grid);

    LocalDate today = LocalDate.now(clock.withZone(KST));
    LocalDate yesterday = today.minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    List<Weather> slots = weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(
        weatherGrid, from);

    BaseTime latestBaseTime = KmaBaseTimeCalculator.calculate(clock.instant());
    Weather todayRepresentative = representativeSlotSelector
        .select(slotsOfDate(slots, today), clock.instant())
        .orElse(null);
    boolean stale = todayRepresentative == null
        || todayRepresentative.getForecastedAt().isBefore(latestBaseTime.toInstant());

    List<Weather> fetched = slots;
    if (stale) {
      List<Weather> refreshed = weatherRefresher.refreshSlots(weatherGrid, grid, latestBaseTime);
      if (refreshed.isEmpty()) {
        log.warn("슬롯 재조회 결과가 비어 기존 슬롯을 사용합니다: 저장 격자 ID={}", weatherGrid.getId());
      } else {
        fetched = mergeByForecastAt(slots, refreshed);
      }
    }

    List<Weather> result = representativesFrom(fetched, today);

    List<String> locationNames = locationResolver.resolveLocationNames(latitude, longitude);
    return result.stream()
        .map(weather -> weatherMapper.toDto(weather, weatherGrid, latitude, longitude,
            locationNames))
        .toList();
  }

  // resolveLocationNamesAsync()가 이미 캐시+single-flight를 내장하고 있어 그대로 재사용한다.
  public CompletableFuture<LocationDto> getLocationAsync(double latitude, double longitude) {
    KmaGridPoint grid = toGrid(latitude, longitude);
    WeatherGrid weatherGrid = locationResolver.resolveWeatherGrid(grid);
    return locationResolver.resolveLocationNamesAsync(latitude, longitude, kakaoLocationExecutor)
        .orTimeout(5, TimeUnit.SECONDS)
        .thenApply(locationNames -> new LocationDto(latitude, longitude, weatherGrid.getX(),
            weatherGrid.getY(), locationNames))
        .exceptionally(ex -> {
          log.warn("위치 조회 타임아웃 또는 실패, 빈 지역명으로 폴백", ex);
          return new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
              List.of());
        });
  }

  public LocationDto getLocation(double latitude, double longitude) {
    KmaGridPoint grid = toGrid(latitude, longitude);
    WeatherGrid weatherGrid = locationResolver.resolveWeatherGrid(grid);
    List<String> locationNames = locationResolver.resolveLocationNames(latitude, longitude);
    return new LocationDto(latitude, longitude, weatherGrid.getX(), weatherGrid.getY(),
        locationNames);
  }

  // 재조회 결과가 일부 forecastAt만 담고 있어도(파서 게이트 등으로 일부 날짜가 빠질 수 있음)
  // 그 forecastAt만 갱신되도록 병합한다 - 재조회에 없는 forecastAt은 기존 DB 슬롯을 그대로 쓴다.
  private List<Weather> mergeByForecastAt(List<Weather> slots, List<Weather> refreshed) {
    Map<Instant, Weather> byForecastAt = new LinkedHashMap<>();
    slots.forEach(slot -> byForecastAt.put(slot.getForecastAt(), slot));
    refreshed.forEach(slot -> byForecastAt.put(slot.getForecastAt(), slot));
    return new ArrayList<>(byForecastAt.values());
  }

  // 오늘 이전 날짜는 제외하고, 날짜별로 대표 슬롯 1건씩만 남긴다 - 오늘은 조회 시각과 가장
  // 가까운 슬롯, 내일 이후는 15시(KST) 고정 기준 슬롯을 대표값으로 삼는다.
  private List<Weather> representativesFrom(List<Weather> slots, LocalDate today) {
    Map<LocalDate, List<Weather>> byDate = slots.stream()
        .filter(w -> !toForecastDate(w).isBefore(today))
        .collect(Collectors.groupingBy(this::toForecastDate, TreeMap::new, Collectors.toList()));

    return byDate.entrySet().stream()
        .map(entry -> representativeSlotSelector
            .select(entry.getValue(), referenceInstant(entry.getKey(), today))
            .orElse(null))
        .filter(Objects::nonNull)
        .toList();
  }

  private List<Weather> slotsOfDate(List<Weather> slots, LocalDate date) {
    return slots.stream().filter(w -> toForecastDate(w).equals(date)).toList();
  }

  private Instant referenceInstant(LocalDate date, LocalDate today) {
    if (date.equals(today)) {
      return clock.instant();
    }
    return date.atTime(FUTURE_REPRESENTATIVE_HOUR, 0).atZone(KST).toInstant();
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