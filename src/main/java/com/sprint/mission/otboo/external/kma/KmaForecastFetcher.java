package com.sprint.mission.otboo.external.kma;

import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import com.sprint.mission.otboo.external.kma.KmaGridConverter.KmaGridPoint;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KmaForecastFetcher {

  private static final int FORECAST_NUM_OF_ROWS = 1000;
  private static final int FORECAST_PAGE_NO = 1;

  private final KmaWeatherClient kmaWeatherClient;
  private final KmaForecastParser kmaForecastParser;
  private final String kmaServiceKey;

  public KmaForecastFetcher(KmaWeatherClient kmaWeatherClient,
      KmaForecastParser kmaForecastParser,
      @Value("${weather.kma.service-key}") String kmaServiceKey) {
    this.kmaWeatherClient = kmaWeatherClient;
    this.kmaForecastParser = kmaForecastParser;
    this.kmaServiceKey = kmaServiceKey;
  }

  public List<DailyWeatherForecastDto> fetch(KmaGridPoint grid, BaseTime baseTime, Instant now) {
    KmaWeatherResponse response = kmaWeatherClient.getVillageForecast(kmaServiceKey,
        FORECAST_NUM_OF_ROWS, FORECAST_PAGE_NO, "JSON", baseTime.baseDate(), baseTime.baseTime(),
        grid.nx(), grid.ny());
    return kmaForecastParser.parseDailyForecast(response, now).stream()
        .sorted(Comparator.comparing(DailyWeatherForecastDto::date))
        .toList();
  }
}