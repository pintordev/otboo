package com.sprint.mission.otboo.batch.weatherfetch.processor;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherRefresher;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import java.util.List;
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
    return weatherRefresher.build(weatherGrid, grid, baseTime);
  }
}