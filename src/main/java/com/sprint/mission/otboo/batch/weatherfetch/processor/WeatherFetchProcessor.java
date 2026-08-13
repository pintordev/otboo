package com.sprint.mission.otboo.batch.weatherfetch.processor;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherRefresher;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@Component
public class WeatherFetchProcessor implements ItemProcessor<WeatherGrid, List<Weather>> {

  private final WeatherRefresher weatherRefresher;
  private final BaseTime baseTime;
  // process() 1회 호출당 KmaForecastFetcher 호출이 정확히 1회 일어난다(WeatherRefresher.fetch()
  // 참고). chunk-scan으로 skip된 항목이 재처리되면 이 값이 그만큼 늘어나므로, 재실행 배수를
  // 로그로 관측하기 위한 카운터다. @StepScope라 Step마다 새 인스턴스가 생겨 Step 단위로 리셋된다.
  private final AtomicInteger kmaCallCount = new AtomicInteger(0);

  public WeatherFetchProcessor(WeatherRefresher weatherRefresher,
      @Value("#{jobExecutionContext['baseDate']}") String baseDate,
      @Value("#{jobExecutionContext['baseTime']}") String baseTimeValue) {
    this.weatherRefresher = weatherRefresher;
    this.baseTime = new BaseTime(baseDate, baseTimeValue);
    log.info("WeatherFetchProcessor baseTime 주입: baseDate={}, baseTime={}", baseDate,
        baseTimeValue);
  }

  @Override
  public List<Weather> process(WeatherGrid weatherGrid) {
    KmaGridPoint grid = new KmaGridPoint(weatherGrid.getX(), weatherGrid.getY());
    log.info("WeatherFetchProcessor KMA 호출: 누적 호출 횟수={}", kmaCallCount.incrementAndGet());
    return weatherRefresher.build(weatherGrid, grid, baseTime);
  }
}