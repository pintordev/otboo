package com.sprint.mission.otboo.batch.weatherfetch.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.RepresentativeSlotSelector;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 청크(그리드 일부) 단위 D0/D1 감지·발행 전용(#163, PR #131 리뷰 - 전체 격자를 한 트랜잭션에서
// 처리하던 문제) - detectAndNotify()가 gridChunkSize만큼 잘라 넘긴 청크를 여기서 트랜잭션
// 하나로 처리한다. 격자 하나의 실제 평가·리셋·발행은 WeatherSuddenChangeGridProcessor에
// 위임한다(#283 CodeRabbit 리뷰) - 한 격자에서 DB 오류가 나도 그 격자만 REQUIRES_NEW
// 트랜잭션 안에서 실패하고, 같은 청크의 나머지 격자는 계속 처리된다.
@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherSuddenChangeChunkProcessor {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final RepresentativeSlotSelector representativeSlotSelector;
  private final WeatherD1BaselineRepository weatherD1BaselineRepository;
  private final WeatherSuddenChangeGridProcessor gridProcessor;
  private final Clock clock;

  // 청크(그리드 일부) 하나를 트랜잭션 하나로 처리한다(#163, PR #131 리뷰 - 전체 격자
  // 단일 트랜잭션 리스크) - detectAndNotify()가 gridChunkSize만큼 나눠 청크별로 이 메서드를
  // 호출한다. shouldHandleD0/shouldHandleD1은 baseTime 기준 라우팅 게이트를 그대로 전달받는다.
  @Transactional
  public ChunkResult process(List<WeatherGrid> chunk, BaseTime baseTime, LocalDate today,
      boolean shouldHandleD0, boolean shouldHandleD1) {
    int d0Notified = shouldHandleD0 ? handleD0(chunk, baseTime, today) : 0;
    int d1Notified = shouldHandleD1 ? handleD1(chunk, today) : 0;
    return new ChunkResult(d0Notified, d1Notified);
  }

  // baseTime 정각 슬롯은 기상청 응답에 없다(슬롯은 baseTime+1h부터 시작 - 실측 확인함) - 당일
  // 슬롯 중 baseTime과 가장 가까운 슬롯을 대표로 삼는다. RepresentativeSlotSelector는 호출부가
  // 이미 같은 날짜로 필터링한 리스트를 넘긴다는 전제로 동작한다 - 그리드별로 당일 범위 쿼리
  // 결과를 미리 그룹핑해서 넘기므로 이 전제를 만족한다.
  int handleD0(List<WeatherGrid> chunk, BaseTime baseTime, LocalDate today) {
    List<UUID> gridIds = chunk.stream().map(WeatherGrid::getId).toList();
    Instant from = today.atStartOfDay(KST).toInstant();
    Instant to = today.plusDays(1).atStartOfDay(KST).toInstant();
    Map<UUID, List<Weather>> slotsByGridId = weatherRepository
        .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
            gridIds, from, to)
        .stream()
        .collect(Collectors.groupingBy(weather -> weather.getWeatherGrid().getId()));

    int notified = 0;
    for (WeatherGrid grid : chunk) {
      try {
        List<Weather> todaySlots = slotsByGridId.getOrDefault(grid.getId(), List.of());
        Optional<Weather> target =
            representativeSlotSelector.select(todaySlots, baseTime.toInstant());
        if (target.isEmpty()) {
          log.warn("당일 슬롯이 없어 D0 평가를 건너뜀: weatherGridId={}, baseTime={}", grid.getId(),
              baseTime.baseTime());
          continue;
        }
        if (!hasCompleteBaseline(target.get())) {
          log.warn("baseline 컬럼 결측으로 D0 평가를 건너뜀: weatherId={}", target.get().getId());
          continue;
        }
        if (gridProcessor.evaluateD0(target.get())) {
          notified++;
        }
      } catch (RuntimeException e) {
        log.error("D0 평가 실패, 다음 격자로 진행: weatherGridId={}", grid.getId(), e);
      }
    }
    return notified;
  }

  // baseline_* 컬럼이 DB 레벨 NOT NULL이라 이론상 도달 불가능하지만, WeatherChangeSnapshot
  // .baselineOf()의 언박싱 NPE를 막는 값싼 방어선이라 남겨둔다.
  private boolean hasCompleteBaseline(Weather weather) {
    return weather.getBaselineTemperatureCurrent() != null
        && weather.getBaselinePrecipitationType() != null
        && weather.getBaselinePrecipitationProbability() != null
        && weather.getBaselinePrecipitationAmount() != null;
  }

  // 오늘의 캡처가 내일의 D1 baseline이 된다 - 매일 20시 배치에서 청크 전체의 D2(오늘+2)
  // 24시간 스냅샷을 쿼리 2번(슬롯 조회 1번, 기존 baseline 조회 1번)으로 upsert한다(#163).
  void captureD2Snapshot(List<WeatherGrid> chunk, LocalDate d2Date) {
    List<UUID> gridIds = chunk.stream().map(WeatherGrid::getId).toList();
    Instant from = d2Date.atStartOfDay(KST).toInstant();
    Instant to = d2Date.plusDays(1).atStartOfDay(KST).toInstant();

    Map<UUID, List<Weather>> slotsByGridId = weatherRepository
        .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
            gridIds, from, to)
        .stream()
        .collect(Collectors.groupingBy(weather -> weather.getWeatherGrid().getId()));
    Map<UUID, WeatherD1Baseline> existingByGridId = weatherD1BaselineRepository
        .findAllByWeatherGridIdInAndTargetDate(gridIds, d2Date)
        .stream()
        .collect(Collectors.toMap(baseline -> baseline.getWeatherGrid().getId(),
            baseline -> baseline));

    List<WeatherD1Baseline> newBaselines = new ArrayList<>();
    for (WeatherGrid grid : chunk) {
      List<Weather> slots = slotsByGridId.getOrDefault(grid.getId(), List.of());
      if (slots.isEmpty()) {
        // 빈 스냅샷을 그대로 저장하면 다음 날 compareD1AndNotify()가 "row는 있는데 전부
        // null"이라는 걸 구분 못 해 경고 없이 조용히 스킵된다 - 아예 저장하지 않고 원인이
        // 로그에 드러나게 한다.
        log.warn("당일 슬롯이 없어 D2 스냅샷 캡처를 건너뜀: weatherGridId={}, d2Date={}",
            grid.getId(), d2Date);
        continue;
      }
      Map<Instant, WeatherChangeSnapshot> hourlySnapshot = slots.stream()
          .collect(Collectors.toMap(Weather::getForecastAt, WeatherChangeSnapshot::currentOf));
      WeatherD1Baseline existing = existingByGridId.get(grid.getId());
      if (existing != null) {
        existing.updateHourlySnapshot(hourlySnapshot, clock.instant());
      } else {
        newBaselines.add(WeatherD1Baseline.create(grid, d2Date, hourlySnapshot, clock.instant()));
      }
    }
    if (!newBaselines.isEmpty()) {
      weatherD1BaselineRepository.saveAll(newBaselines);
    }
  }

  // 어제 20시에 captureD2Snapshot()이 캡처해둔 D1 baseline과 오늘 24개 시각을 각각 독립적으로
  // 비교한다 - 평균/최댓값 요약 없이 시각별로 그대로 비교(#163). 소비된 baseline row는 여기서
  // 지우지 않는다 - target_date가 매일 전진해 재사용될 일이 없으므로, retention 배치의
  // cutoff(오늘) 삭제에 맡긴다(#163, PR #131 리뷰).
  int compareD1AndNotify(List<WeatherGrid> chunk, LocalDate d1Date) {
    List<UUID> gridIds = chunk.stream().map(WeatherGrid::getId).toList();
    List<WeatherD1Baseline> baselineRows = weatherD1BaselineRepository
        .findAllByWeatherGridIdInAndTargetDate(gridIds, d1Date);
    if (baselineRows.isEmpty()) {
      // 원인은 하나(어제 20시 캡처 누락)인데 그리드 수만큼 반복 로그를 남기면 안 되므로
      // 청크당 집계 로그 1줄로 남긴다.
      log.warn("D1 baseline 스냅샷 없음: 청크 내 격자 {}건 전체, date={} - 어제 20시 캡처가 "
          + "안 됐거나 슬롯 결측", chunk.size(), d1Date);
      return 0;
    }
    Map<UUID, WeatherD1Baseline> baselineByGridId = baselineRows.stream()
        .collect(Collectors.toMap(baseline -> baseline.getWeatherGrid().getId(),
            baseline -> baseline));

    Instant from = d1Date.atStartOfDay(KST).toInstant();
    Instant to = d1Date.plusDays(1).atStartOfDay(KST).toInstant();
    Map<UUID, List<Weather>> currentByGridId = weatherRepository
        .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
            gridIds, from, to)
        .stream()
        .collect(Collectors.groupingBy(weather -> weather.getWeatherGrid().getId()));

    int notified = 0;
    for (WeatherGrid grid : chunk) {
      WeatherD1Baseline baselineRow = baselineByGridId.get(grid.getId());
      if (baselineRow == null) {
        log.warn("D1 baseline 스냅샷 없음: grid={}, date={} - 어제 20시 캡처가 안 됐거나 슬롯 결측",
            grid.getId(), d1Date);
        continue;
      }
      try {
        Map<Instant, WeatherChangeSnapshot> baselineByHour = baselineRow.getHourlySnapshot();
        // 하루 요약(evaluateDaySummary)으로 grid당 1회만 평가한다 - 24개 시각을 개별로 비교해
        // reasons를 이어붙이면 같은 문구가 시각 수만큼 반복될 수 있다.
        List<WeatherChangeSnapshot> baselineSnapshots = new ArrayList<>();
        List<WeatherChangeSnapshot> currentSnapshots = new ArrayList<>();
        for (Weather current : currentByGridId.getOrDefault(grid.getId(), List.of())) {
          WeatherChangeSnapshot baseline = baselineByHour.get(current.getForecastAt());
          if (baseline == null) {
            continue; // 어제는 없었던 슬롯(경계 케이스) - 비교 스킵
          }
          baselineSnapshots.add(baseline);
          currentSnapshots.add(WeatherChangeSnapshot.currentOf(current));
        }
        if (gridProcessor.evaluateD1(grid, baselineSnapshots, currentSnapshots)) {
          notified++;
        }
      } catch (RuntimeException e) {
        log.error("D1 평가 실패, 다음 격자로 진행: weatherGridId={}, date={}", grid.getId(), d1Date,
            e);
      }
    }
    return notified;
  }

  // 오늘의 캡처가 내일의 D1 baseline이 되고, 오늘의 비교는 어제의 캡처를 baseline으로 쓴다 -
  // 매일 반복되면서 "어제 20시 → 오늘 20시" 체인이 자연히 만들어진다.
  int handleD1(List<WeatherGrid> chunk, LocalDate today) {
    LocalDate d1Date = today.plusDays(1);
    LocalDate d2Date = today.plusDays(2);

    captureD2Snapshot(chunk, d2Date);
    return compareD1AndNotify(chunk, d1Date);
  }

  public record ChunkResult(int d0Notified, int d1Notified) {

  }
}