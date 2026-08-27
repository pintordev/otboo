package com.sprint.mission.otboo.batch.weatherfetch.service;

import com.sprint.mission.otboo.domain.authuser.user.entity.Profile;
import com.sprint.mission.otboo.domain.authuser.user.repository.ProfileRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeEvaluator;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 격자 하나의 D0/D1 평가·baseline 리셋·발행을 독립 트랜잭션(REQUIRES_NEW)으로 처리한다(#283
// CodeRabbit 리뷰) - WeatherSuddenChangeChunkProcessor의 청크 트랜잭션과 분리해, 한 격자에서
// DB 오류가 나도 커넥션이 abort 상태가 돼 같은 청크의 나머지 격자까지 실패하는 걸 막는다.
@RequiredArgsConstructor
@Component
public class WeatherSuddenChangeGridProcessor {

  private final WeatherRepository weatherRepository;
  private final ProfileRepository profileRepository;
  private final WeatherChangeEvaluator weatherChangeEvaluator;
  private final ApplicationEventPublisher eventPublisher;

  // baseTime과 forecastAt이 정확히 일치해 이 슬롯은 하루에 한 번만 D0 평가된다 - 리셋 여부이
  // 이후 재평가에 영향을 줄 일이 없으므로, 변경이 감지되면 수신자 유무와 무관하게 리셋한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean evaluateD0(Weather weather) {
    Optional<WeatherChangeEvaluator.ChangeResult> result = weatherChangeEvaluator.evaluate(
        WeatherChangeSnapshot.baselineOf(weather), WeatherChangeSnapshot.currentOf(weather));
    if (result.isEmpty()) {
      return false;
    }
    resetBaseline(weather);
    return publish(weather.getWeatherGrid(), result.get());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean evaluateD1(WeatherGrid grid, List<WeatherChangeSnapshot> baselineSnapshots,
      List<WeatherChangeSnapshot> currentSnapshots) {
    Optional<WeatherChangeEvaluator.ChangeResult> result =
        weatherChangeEvaluator.evaluateDaySummary(baselineSnapshots, currentSnapshots);
    return result.isPresent() && publish(grid, result.get());
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

  // LocationRequest.locationNames가 null로
  // 등록될 수 있다 - UserMapper.locationDtoFrom()과 동일하게 소비하는 쪽에서 방어한다
  private List<String> normalizedLocationNames(List<String> locationNames) {
    return locationNames == null ? List.of() : locationNames;
  }
}