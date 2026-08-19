package com.sprint.mission.otboo.batch.weatherfetch.scheduler;

import com.sprint.mission.otboo.batch.weatherfetch.service.WeatherFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherFetchScheduler {

  private final WeatherFetchService weatherFetchService;

  @SchedulerLock(
      name = "WeatherFetchBatchSchedulerLock",
      lockAtMostFor = "${batch.weather-fetch.lock-at-most-for:PT10M}",
      lockAtLeastFor = "${batch.weather-fetch.lock-at-least-for:PT1M}")
  @Scheduled(cron = "0 30 2,5,8,11,14,17,20,23 * * *", zone = "Asia/Seoul")
  public void fetch() {
    log.info("날씨 수집 배치 시작");
    weatherFetchService.execute();
    log.info("날씨 수집 배치 완료");
  }
}