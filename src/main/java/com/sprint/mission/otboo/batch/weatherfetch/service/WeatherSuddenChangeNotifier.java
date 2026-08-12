package com.sprint.mission.otboo.batch.weatherfetch.service;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.WeatherChangeNotificationLog;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.WeatherChangeNotificationLogRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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

@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherSuddenChangeNotifier {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String EVENING_BASE_TIME = "2000";
  private static final String LAST_BASE_TIME = "2300";

  private final WeatherRepository weatherRepository;
  private final ProfileRepository profileRepository;
  private final WeatherChangeNotificationLogRepository notificationLogRepository;
  private final WeatherChangeEvaluator weatherChangeEvaluator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void detectAndNotify(BaseTime baseTime) {
    List<WeatherGrid> updatedGrids = weatherRepository.findGridsUpdatedAt(baseTime.toInstant());
    LocalDate today = LocalDate.now(clock.withZone(KST));
    List<Instant> targetForecastAts = resolveTargetForecastAts(baseTime, today);

    notificationLogRepository.deleteByForecastAtBefore(today.atStartOfDay(KST).toInstant());

    int notifiedCount = 0;
    for (WeatherGrid grid : updatedGrids) {
      notifiedCount += evaluateAndNotify(grid, targetForecastAts);
    }
    log.info("날씨 급변 감지 완료: 평가 격자 수={}, 알림 발행 격자 수={}", updatedGrids.size(), notifiedCount);
  }

  // 23:30 회차는 D0/D1 둘 다 빠져 빈 리스트가 될 수 있다 - evaluateAndNotify()가 이를 처리한다.
  private List<Instant> resolveTargetForecastAts(BaseTime baseTime, LocalDate today) {
    List<Instant> targets = new ArrayList<>();
    if (!baseTime.baseTime().equals(LAST_BASE_TIME)) {
      targets.add(today.atStartOfDay(KST).toInstant());
    }
    if (baseTime.baseTime().equals(EVENING_BASE_TIME)) {
      targets.add(today.plusDays(1).atStartOfDay(KST).toInstant());
    }
    return targets;
  }

  private int evaluateAndNotify(WeatherGrid grid, List<Instant> targetForecastAts) {
    if (targetForecastAts.isEmpty()) {
      return 0;
    }
    Map<Instant, List<Weather>> byForecastAt = weatherRepository
        .findRecentTwoRevisions(grid, targetForecastAts).stream()
        .collect(Collectors.groupingBy(Weather::getForecastAt));

    int notified = 0;
    for (Map.Entry<Instant, List<Weather>> entry : byForecastAt.entrySet()) {
      Instant forecastAt = entry.getKey();
      List<Weather> revisions = entry.getValue();
      if (revisions.size() < 2) {
        continue;
      }
      // 네이티브 쿼리의 ORDER BY는 groupingBy 이후 값 리스트 순서까지 보장하지 않으므로
      // forecastedAt 내림차순으로 명시적으로 재정렬 - get(0)이 항상 최신임을 코드로 보장한다
      List<Weather> sorted = revisions.stream()
          .sorted(Comparator.comparing(Weather::getForecastedAt).reversed())
          .toList();
      Weather latest = sorted.get(0);
      Weather baseline = resolveBaseline(grid, forecastAt, sorted.get(1));
      Optional<WeatherChangeEvaluator.ChangeResult> result =
          weatherChangeEvaluator.evaluate(baseline, latest);
      if (result.isPresent() && publish(result.get())) {
        notified++;
      }
    }
    return notified;
  }

  // 이미 이 예보일로 알림을 보낸 적 있으면 "직전 리비전"이 아니라 "마지막 알림 기준 리비전"과
  // 비교한다 - 노이즈성 재발행은 막고, 그 이후 진짜 더 벌어진 변화는 여전히 잡는다.
  private Weather resolveBaseline(WeatherGrid grid, Instant forecastAt, Weather previousRevision) {
    return notificationLogRepository.findByWeatherGridAndForecastAt(grid, forecastAt)
        .map(WeatherChangeNotificationLog::getLastNotifiedForecastedAt)
        .flatMap(lastNotifiedForecastedAt -> weatherRepository
            .findByWeatherGridAndForecastAtAndForecastedAt(grid, forecastAt,
                lastNotifiedForecastedAt)
            .or(() -> {
              log.warn("알림 baseline 리비전을 찾지 못해 직전 리비전으로 대체: grid={}, forecastAt={}, "
                  + "lastNotifiedForecastedAt={}", grid.getId(), forecastAt,
                  lastNotifiedForecastedAt);
              return Optional.empty();
            }))
        .orElse(previousRevision);
  }

  private boolean publish(WeatherChangeEvaluator.ChangeResult result) {
    WeatherGrid grid = result.weatherGrid();
    List<Profile> profiles = profileRepository.findByLocation(grid.getX(), grid.getY());
    if (profiles.isEmpty()) {
      return false;
    }
    List<UUID> receiverIds = profiles.stream().map(Profile::getId).toList();
    // LocationRequest.locationNames엔 @NotNull이 없어(api-docs.json에도 required 아님) null로
    // 등록될 수 있다 - UserMapper.locationDtoFrom()과 동일하게 소비하는 쪽에서 방어한다
    List<String> locationNames = profiles.get(0).getLocation().getLocationNames();
    String regionName = (locationNames == null || locationNames.isEmpty()) ? ""
        : locationNames.get(locationNames.size() - 1) + " ";
    String content = regionName + String.join(" ", result.reasons());
    eventPublisher.publishEvent(new NotificationRequestedEvent(
        Set.copyOf(receiverIds), "날씨 급변", content, NotificationLevel.WARNING));
    recordNotified(grid, result.forecastAt(), result.latestForecastedAt());
    return true;
  }

  private void recordNotified(WeatherGrid grid, Instant forecastAt, Instant latestForecastedAt) {
    notificationLogRepository.findByWeatherGridAndForecastAt(grid, forecastAt)
        .ifPresentOrElse(
            existing -> existing.updateLastNotified(latestForecastedAt),
            () -> notificationLogRepository.save(
                WeatherChangeNotificationLog.create(grid, forecastAt, latestForecastedAt)));
  }
}