package com.sprint.mission.otboo.batch.weatherfetch.service;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 청크(그리드 일부) 단위 D0/D1 감지·발행 전용(#163, PR #131 리뷰 - 전체 격자를 한 트랜잭션에서
// 처리하던 문제) - detectAndNotify()가 gridChunkSize만큼 잘라 넘긴 청크를 여기서 트랜잭션
// 하나로 처리한다.
@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherSuddenChangeChunkProcessor {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final ProfileRepository profileRepository;
  private final WeatherD1BaselineRepository weatherD1BaselineRepository;
  private final WeatherChangeEvaluator weatherChangeEvaluator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  // 청크(그리드 일부) 하나를 트랜잭션 하나로 처리한다(#163, PR #131 리뷰 - 전체 격자
  // 단일 트랜잭션 리스크) - detectAndNotify()가 gridChunkSize만큼 나눠 청크별로 이 메서드를
  // 호출한다. shouldHandleD0/shouldHandleD1은 baseTime 기준 라우팅 게이트를 그대로 전달받는다.
  @Transactional
  public ChunkResult process(List<WeatherGrid> chunk, BaseTime baseTime, LocalDate today,
      boolean shouldHandleD0, boolean shouldHandleD1) {
    int d0Notified = shouldHandleD0 ? handleD0(chunk, baseTime) : 0;
    int d1Notified = shouldHandleD1 ? handleD1(chunk, today) : 0;
    return new ChunkResult(d0Notified, d1Notified);
  }

  // baseTime과 정확히 일치하는 슬롯만 청크 전체에 대해 쿼리 1번으로 가져온다 - 그리드마다
  // 따로 조회하지 않으므로 N+1이 없다(#163). RepresentativeSlotSelector는 여기서 필요 없다 -
  // baseTime은 근접일 그리드와 항상 거리 0으로 정확히 일치한다.
  int handleD0(List<WeatherGrid> chunk, BaseTime baseTime) {
    List<UUID> gridIds = chunk.stream().map(WeatherGrid::getId).toList();
    List<Weather> targets = weatherRepository
        .findAllByWeatherGridIdInAndForecastAt(gridIds, baseTime.toInstant());

    int notified = 0;
    for (Weather target : targets) {
      if (!hasCompleteBaseline(target)) {
        log.warn("baseline 컬럼 결측으로 D0 평가를 건너뜀: weatherId={}", target.getId());
        continue;
      }
      if (evaluateD0(target)) {
        notified++;
      }
    }
    return notified;
  }

  // 정상 쓰기 경로(WeatherWriter.buildSlots())에서는 baseline_*이 첫 insert 때 항상 함께
  // 채워지므로 실제로는 도달하지 않지만, WeatherChangeSnapshot.baselineOf()의 언박싱
  // NPE를 방어한다.
  private boolean hasCompleteBaseline(Weather weather) {
    return weather.getBaselineTemperatureCurrent() != null
        && weather.getBaselinePrecipitationType() != null
        && weather.getBaselinePrecipitationProbability() != null
        && weather.getBaselinePrecipitationAmount() != null;
  }

  // baseTime과 forecastAt이 정확히 일치해 이 슬롯은 하루에 한 번만 D0 평가된다 - 리셋 여부가
  // 이후 재평가에 영향을 줄 일이 없으므로, 변경이 감지되면 수신자 유무와 무관하게 리셋한다.
  // notified 카운트(로그용)만 실제 발행 성공 여부를 따른다.
  private boolean evaluateD0(Weather weather) {
    Optional<WeatherChangeEvaluator.ChangeResult> result = weatherChangeEvaluator.evaluate(
        WeatherChangeSnapshot.baselineOf(weather), WeatherChangeSnapshot.currentOf(weather));
    if (result.isEmpty()) {
      return false;
    }
    resetBaseline(weather);
    return publish(weather.getWeatherGrid(), result.get());
  }

  private void resetBaseline(Weather weather) {
    weatherRepository.updateBaseline(weather.getId(), weather.getTemperatureCurrent(),
        weather.getPrecipitationType(), weather.getPrecipitationProbability(),
        weather.getPrecipitationAmount());
  }

  // D0/D1이 공유하는 단순화된 발행 - notificationLog 없이 이벤트 발행만 한다.
  private boolean publish(WeatherGrid grid, WeatherChangeEvaluator.ChangeResult result) {
    List<Profile> profiles = profileRepository.findByLocation(grid.getX(), grid.getY());
    if (profiles.isEmpty()) {
      return false;
    }
    Map<List<String>, List<UUID>> receiverIdsByRegion = profiles.stream()
        .collect(Collectors.groupingBy(
            profile -> normalizedLocationNames(profile.getLocation().getLocationNames()),
            Collectors.mapping(Profile::getId, Collectors.toList())));

    for (Map.Entry<List<String>, List<UUID>> entry : receiverIdsByRegion.entrySet()) {
      List<String> locationNames = entry.getKey();
      String regionName = locationNames.isEmpty() ? ""
          : locationNames.get(locationNames.size() - 1) + " ";
      String content = result.content(regionName);
      eventPublisher.publishEvent(new NotificationRequestedEvent(
          Set.copyOf(entry.getValue()), "날씨 급변", content, NotificationLevel.WARNING));
    }
    return true;
  }

  // LocationRequest.locationNames엔 @NotNull이 없어(api-docs.json에도 required 아님) null로
  // 등록될 수 있다 - UserMapper.locationDtoFrom()과 동일하게 소비하는 쪽에서 방어한다
  private List<String> normalizedLocationNames(List<String> locationNames) {
    return locationNames == null ? List.of() : locationNames;
  }

  // 오늘의 캡처가 내일의 D1 baseline이 된다 - 매일 20시 배치에서 청크 전체의 D2(오늘+2)
  // 24시간 스냅샷을 쿼리 2번(슬롯 조회 1번, 기존 baseline 조회 1번)으로 upsert한다(#163).
  void captureD2Snapshot(List<WeatherGrid> chunk, LocalDate d2Date) {
    List<UUID> gridIds = chunk.stream().map(WeatherGrid::getId).toList();
    Instant from = d2Date.atStartOfDay(KST).toInstant();
    Instant to = d2Date.plusDays(1).atStartOfDay(KST).toInstant();

    Map<UUID, List<Weather>> slotsByGridId = weatherRepository
        .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(gridIds, from,
            to)
        .stream()
        .collect(Collectors.groupingBy(weather -> weather.getWeatherGrid().getId()));
    Map<UUID, WeatherD1Baseline> existingByGridId = weatherD1BaselineRepository
        .findAllByWeatherGridIdInAndTargetDate(gridIds, d2Date)
        .stream()
        .collect(Collectors.toMap(baseline -> baseline.getWeatherGrid().getId(),
            baseline -> baseline));

    List<WeatherD1Baseline> newBaselines = new ArrayList<>();
    for (WeatherGrid grid : chunk) {
      Map<Instant, WeatherChangeSnapshot> hourlySnapshot = slotsByGridId
          .getOrDefault(grid.getId(), List.of()).stream()
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
        .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan(gridIds, from,
            to)
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
      Map<Instant, WeatherChangeSnapshot> baselineByHour = baselineRow.getHourlySnapshot();
      // 24개 시각 중 여러 시각이 동시에 임계값을 넘어도 grid당 알림은 1건이면 된다 -
      // reasons를 모아뒀다가 publish는 grid당 1회만 호출한다.
      List<String> reasons = new ArrayList<>();
      for (Weather current : currentByGridId.getOrDefault(grid.getId(), List.of())) {
        WeatherChangeSnapshot baseline = baselineByHour.get(current.getForecastAt());
        if (baseline == null) {
          continue; // 어제는 없었던 슬롯(경계 케이스) - 비교 스킵
        }
        weatherChangeEvaluator.evaluate(baseline, WeatherChangeSnapshot.currentOf(current))
            .ifPresent(result -> reasons.addAll(result.reasons()));
      }
      if (!reasons.isEmpty() && publish(grid, new WeatherChangeEvaluator.ChangeResult(reasons))) {
        notified++;
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