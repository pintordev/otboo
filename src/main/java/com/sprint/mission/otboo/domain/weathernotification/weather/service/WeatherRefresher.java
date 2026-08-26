package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.singleflight.SingleFlightRegistry;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaForecastFetcher;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 클래스 레벨 @Transactional(readOnly = true)를 의도적으로 두지 않는다 - API 경로뿐 아니라
// 배치(WeatherFetchProcessor) 청크 트랜잭션 안에서도 호출되는데, 그 안에서 readOnly로
// 참여하면 배치가 관리하는 flush/커밋 타이밍과 충돌해 정상 처리된 항목이 유실된다(실측 확인).
// 쓰기가 필요한 지점(WeatherWriter.saveSlots())은 이미 자체 REQUIRES_NEW를 갖고 있고, 읽기 쿼리는
// Spring Data JPA repository 메서드 자체가 개별적으로 트랜잭션을 처리하므로 감싸는 애너테이션이
// 없어도 정상 동작한다.
@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherRefresher {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final KmaForecastFetcher kmaForecastFetcher;
  private final WeatherWriter weatherWriter;
  private final Clock clock;
  private final SingleFlightRegistry singleFlightRegistry;
  private final WeatherCacheProvider weatherCacheProvider;
  private final RepresentativeSlotSelector representativeSlotSelector;

  private final ConcurrentHashMap<InFlightKey, CompletableFuture<List<Weather>>> inFlight =
      new ConcurrentHashMap<>();

  // 로컬 in-flight 다음에 SingleFlightRegistry(분산 락)까지 태우는 2단 방어.
  public CompletableFuture<List<Weather>> refreshSlotsAsync(WeatherGrid weatherGrid,
      KmaGridPoint grid, BaseTime baseTime, List<Weather> dbSlots, Executor executor) {
    InFlightKey key = new InFlightKey(weatherGrid.getId(), baseTime);
    String lockKey =
        "weather:" + weatherGrid.getId() + ":" + baseTime.baseDate() + baseTime.baseTime();
    CompletableFuture<List<Weather>> future = inFlight.computeIfAbsent(key, k ->
        singleFlightRegistry.execute(
            lockKey,
            () -> {
              List<Weather> refreshed = refreshSlots(weatherGrid, grid, baseTime);
              List<Weather> merged = refreshed.isEmpty()
                  ? dbSlots : mergeByForecastAt(dbSlots, refreshed);
              weatherCacheProvider.putSlots(weatherGrid, merged);
              return merged;
            },
            executor,
            () -> {
              List<Weather> cached = weatherCacheProvider.findCachedSlots(weatherGrid);
              // WeatherService.isStale과 동일하게 "오늘 대표 슬롯" 기준으로만 신선도를 판단한다.
              // 슬롯 하나라도 최신이면 통과시키면, 부분 갱신(파서 게이트로 일부 날짜 누락)된
              // 미래 슬롯만 최신이고 오늘 슬롯은 stale인 경우를 신선함으로 오판한다.
              LocalDate today = LocalDate.now(clock.withZone(KST));
              boolean stale = representativeSlotSelector.isStale(cached, today, clock.instant(),
                  baseTime);
              return stale ? Optional.empty() : Optional.of(cached);
            }));
    // singleFlightRegistry.execute가 이미 완료된 future를 반환하면 whenComplete가
    // 즉시(동기) 실행되므로, computeIfAbsent 밖에서 맵을 수정해야 재귀 수정을 피할 수 있다
    future.whenComplete((r, e) -> inFlight.remove(key, future));
    return future;
  }

  private List<Weather> mergeByForecastAt(List<Weather> slots, List<Weather> refreshed) {
    Map<Instant, Weather> byForecastAt = new LinkedHashMap<>();
    slots.forEach(slot -> byForecastAt.put(slot.getForecastAt(), slot));
    refreshed.forEach(slot -> byForecastAt.put(slot.getForecastAt(), slot));
    return new ArrayList<>(byForecastAt.values());
  }

  private record InFlightKey(UUID weatherGridId, BaseTime baseTime) {

  }

  public List<Weather> refreshSlots(WeatherGrid weatherGrid, KmaGridPoint grid,
      BaseTime baseTime) {
    FetchedSlots fetched = fetchSlots(weatherGrid, grid, baseTime);
    List<Weather> saved = weatherWriter.saveSlots(weatherGrid, baseTime.toInstant(),
        fetched.slotForecasts(), fetched.existingBySlot());
    log.info("기상청 라이브 재조회 저장 완료(슬롯): 저장 건수={}", saved.size());
    return saved;
  }

  // build()의 슬롯 단위 대체
  public List<Weather> buildSlots(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {
    FetchedSlots fetched = fetchSlots(weatherGrid, grid, baseTime);
    return weatherWriter.buildSlots(weatherGrid, baseTime.toInstant(), fetched.slotForecasts(),
        fetched.existingBySlot());
  }

  private FetchedSlots fetchSlots(WeatherGrid weatherGrid, KmaGridPoint grid, BaseTime baseTime) {
    LocalDate yesterday = LocalDate.now(clock.withZone(KST)).minusDays(1);
    Instant from = yesterday.atStartOfDay(KST).toInstant();
    // existingByDate(LocalDate 키)와 달리 슬롯은 forecastAt(Instant)이 이미 유일 키다 -
    // 날짜로 뭉뚱그려 대표 1건만 남길 필요가 없다.
    Map<Instant, Weather> existingBySlot = weatherRepository
        .findAllByWeatherGridAndForecastAtGreaterThanEqual(weatherGrid, from)
        .stream()
        .collect(Collectors.toMap(Weather::getForecastAt, w -> w,
            (existing, replacement) -> existing));

    log.info("기상청 라이브 재조회(슬롯): baseDate={}, baseTime={}", baseTime.baseDate(),
        baseTime.baseTime());
    List<WeatherForecastSlotDto> slotForecasts = kmaForecastFetcher.fetchSlots(grid, baseTime);
    return new FetchedSlots(slotForecasts, existingBySlot);
  }

  private record FetchedSlots(List<WeatherForecastSlotDto> slotForecasts,
      Map<Instant, Weather> existingBySlot) {

  }
}