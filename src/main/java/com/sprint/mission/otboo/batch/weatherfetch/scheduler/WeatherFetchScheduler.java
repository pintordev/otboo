package com.sprint.mission.otboo.batch.weatherfetch.scheduler;

import com.sprint.mission.otboo.batch.weatherfetch.service.WeatherFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherFetchScheduler {

  private final WeatherFetchService weatherFetchService;

  @Scheduled(cron = "0 30 2,5,8,11,14,17,20,23 * * *", zone = "Asia/Seoul")
  public void fetch() {
    log.info("날씨 수집 배치 시작");
    weatherFetchService.execute();
    log.info("날씨 수집 배치 완료");
  }
}