package com.sprint.mission.otboo.batch.weatherretention.scheduler;

import com.sprint.mission.otboo.batch.weatherretention.service.WeatherRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 전제: 단일 인스턴스에서만 실행된다. WeatherRetentionService.execute()가 time=Instant.now()로
// 매회 다른 식별 파라미터(JobParameters)를 만들어 호출할 때마다 새 JobInstance가 생성되므로,
// Spring Batch의 기본 중복 실행 차단(같은 JobInstance 재실행 방지)이 무력화되는 게 아니라
// 애초에 "중복"으로 잡히지 않는 것이다. 인스턴스가 늘어나면 서로 다른 time 값으로 동시에
// 실행될 수 있으므로, 그 전에 동시 실행 방지 장치(ShedLock 등)를 먼저 추가해야 한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherRetentionScheduler {

  private final WeatherRetentionService weatherRetentionService;

  @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
  public void cleanUp() {
    log.info("날씨 retention 배치 시작");
    weatherRetentionService.execute();
    log.info("날씨 retention 배치 완료");
  }
}