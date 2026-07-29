package com.sprint.mission.otboo.domain.weathernotification.weather.batch;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class WeatherFetchProcessor implements ItemProcessor<WeatherGrid, WeatherFetchItem> {

  private final Clock clock;

  private BaseTime baseTime;

  @Override
  public WeatherFetchItem process(WeatherGrid weatherGrid) {
    if (baseTime == null) {
      baseTime = KmaBaseTimeCalculator.calculate(clock.instant());
      log.info("WeatherFetchProcessor baseTime 계산: baseDate={}, baseTime={}",
          baseTime.baseDate(), baseTime.baseTime());
    }

    KmaGridPoint grid = new KmaGridPoint(weatherGrid.getX(), weatherGrid.getY());
    return new WeatherFetchItem(weatherGrid, grid, baseTime);
  }
}