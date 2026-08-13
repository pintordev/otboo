package com.sprint.mission.otboo.batch.weatherretention.scheduler;

import com.sprint.mission.otboo.batch.weatherretention.service.WeatherRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 전제: 단일 인스턴스에서만 실행된다. WeatherRetentionService.execute()가 time=Instant.now()로
// 매회 새 JobParameters를 만들어 Spring Batch 기본 중복 실행 차단이 비활성화돼 있으므로, 인스턴스가
// 늘어나면 동시 실행 방지 장치(ShedLock 등)를 먼저 추가해야 한다.
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