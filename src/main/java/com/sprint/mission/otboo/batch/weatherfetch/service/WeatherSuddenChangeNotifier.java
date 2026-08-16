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
  private final WeatherD1BaselineRepository weatherD1BaselineRepository;
  private final WeatherChangeEvaluator weatherChangeEvaluator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional
  public void detectAndNotify(BaseTime baseTime) {
    List<WeatherGrid> updatedGrids = weatherRepository.findGridsUpdatedAt(baseTime.toInstant());
    if (updatedGrids.isEmpty()) {
      return;
    }

    int d0Notified = 0;
    if (!baseTime.baseTime().equals(LAST_BASE_TIME)) {
      d0Notified = handleD0(updatedGrids, baseTime);
    }
    log.info("날씨 급변 감지 완료: 평가 격자 수={}, D0 알림={}", updatedGrids.size(), d0Notified);
  }

  // baseTime과 정확히 일치하는 슬롯만 그리드 전체에 대해 쿼리 1번으로 가져온다 - 그리드마다
  // 따로 조회하지 않으므로 N+1이 없다(#163). RepresentativeSlotSelector는 여기서 필요 없다 -
  // baseTime은 근접일 그리드와 항상 거리 0으로 정확히 일치한다.
  int handleD0(List<WeatherGrid> updatedGrids, BaseTime baseTime) {
    List<UUID> gridIds = updatedGrids.stream().map(WeatherGrid::getId).toList();
    List<Weather> targets = weatherRepository
        .findAllByWeatherGridIdInAndForecastAt(gridIds, baseTime.toInstant());

    int notified = 0;
    for (Weather target : targets) {
      if (evaluateD0(target)) {
        notified++;
      }
    }
    return notified;
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
      String content = regionName + String.join(" ", result.reasons());
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

  // 오늘의 캡처가 내일의 D1 baseline이 된다 - 매일 20시 배치에서 D2(오늘+2) 24시간 스냅샷을
  // upsert해두면, 이틀 뒤 그 날짜가 D1이 됐을 때 어제 캡처해둔 값과 비교할 수 있다.
  void captureD2Snapshot(WeatherGrid grid, LocalDate d2Date) {
    Instant from = d2Date.atStartOfDay(KST).toInstant();
    Instant to = d2Date.plusDays(1).atStartOfDay(KST).toInstant();
    Map<Instant, WeatherChangeSnapshot> hourlySnapshot = weatherRepository
        .findAllByWeatherGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(grid, from, to)
        .stream()
        .collect(Collectors.toMap(Weather::getForecastAt, WeatherChangeSnapshot::currentOf));

    weatherD1BaselineRepository.findByWeatherGridAndTargetDate(grid, d2Date)
        .ifPresentOrElse(
            existing -> existing.updateHourlySnapshot(hourlySnapshot, clock.instant()),
            () -> weatherD1BaselineRepository.save(
                WeatherD1Baseline.create(grid, d2Date, hourlySnapshot, clock.instant())));
  }
}