package com.sprint.mission.otboo.batch.weatherretention.scheduler;

import com.sprint.mission.otboo.batch.weatherretention.service.WeatherRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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