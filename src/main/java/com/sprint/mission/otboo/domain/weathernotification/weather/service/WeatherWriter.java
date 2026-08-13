package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.DailyWeatherForecastDto;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 클래스 레벨 @Transactional(readOnly = true)를 의도적으로 두지 않는다 - WeatherRefresher와
// 동일한 이유로, 배치(WeatherFetchWriter) 청크 트랜잭션 안에서도 build()가 호출된다. save()는
// 이미 REQUIRES_NEW로 API 전용 쓰기 경계를 명시하고, build()는 순수 변환이라 트랜잭션이 필요 없다.
@RequiredArgsConstructor
@Component
public class WeatherWriter {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  // WeatherFetchWriter(batch/weatherfetch/writer/)와 같은 형태의 INSERT지만, API 경로는
  // 도메인 서비스 레이어라 별도로 둔다.
  private static final String INSERT_SQL = """
      INSERT INTO weathers (id, weather_grid_id, forecasted_at, forecast_at, sky_status,
          precipitation_type, precipitation_amount, precipitation_probability,
          humidity_current, humidity_compared, temperature_current, temperature_compared,
          temperature_min, temperature_max, wind_speed, wind_as_word, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
      ON CONFLICT (weather_grid_id, forecast_at, forecasted_at) DO NOTHING
      """;

  private final WeatherRepository weatherRepository;
  private final JdbcTemplate jdbcTemplate;

  public List<Weather> build(WeatherGrid weatherGrid, Instant forecastedAt,
      List<DailyWeatherForecastDto> dailyForecasts, Map<LocalDate, Weather> existingByDate) {
    Weather previousDayWeather = dailyForecasts.isEmpty() ? null
        : existingByDate.get(dailyForecasts.get(0).date().minusDays(1));
    Double previousTemp =
        previousDayWeather != null ? previousDayWeather.getTemperatureCurrent() : null;
    Double previousHumidity =
        previousDayWeather != null ? previousDayWeather.getHumidityCurrent() : null;

    List<Weather> built = new ArrayList<>();
    for (DailyWeatherForecastDto dto : dailyForecasts) {
      double temperatureCompared =
          previousTemp != null ? dto.temperatureCurrent() - previousTemp : 0.0;
      double humidityCompared =
          previousHumidity != null ? dto.humidityCurrent() - previousHumidity : 0.0;

      built.add(Weather.create(weatherGrid, forecastedAt,
          dto.date().atStartOfDay(KST).toInstant(), dto.skyStatus(), dto.precipitationType(),
          dto.precipitationAmount(), dto.precipitationProbability(), dto.humidityCurrent(),
          humidityCompared, dto.temperatureCurrent(), temperatureCompared, dto.temperatureMin(),
          dto.temperatureMax(), dto.windSpeed(), toWindStrength(dto.windSpeed())));

      previousTemp = dto.temperatureCurrent();
      previousHumidity = dto.humidityCurrent();
    }
    return built;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Weather> save(WeatherGrid weatherGrid, Instant forecastedAt,
      List<DailyWeatherForecastDto> dailyForecasts, Map<LocalDate, Weather> existingByDate) {
    List<Weather> built = build(weatherGrid, forecastedAt, dailyForecasts, existingByDate);
    if (built.isEmpty()) {
      return built;
    }

    jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        Weather weather = built.get(i);
        ps.setObject(1, UUID.randomUUID());
        ps.setObject(2, weatherGrid.getId());
        ps.setTimestamp(3, Timestamp.from(forecastedAt));
        ps.setTimestamp(4, Timestamp.from(weather.getForecastAt()));
        ps.setString(5, weather.getSkyStatus().name());
        ps.setString(6, weather.getPrecipitationType().name());
        ps.setDouble(7, weather.getPrecipitationAmount());
        ps.setDouble(8, weather.getPrecipitationProbability());
        ps.setDouble(9, weather.getHumidityCurrent());
        ps.setDouble(10, weather.getHumidityCompared());
        ps.setDouble(11, weather.getTemperatureCurrent());
        ps.setDouble(12, weather.getTemperatureCompared());
        ps.setDouble(13, weather.getTemperatureMin());
        ps.setDouble(14, weather.getTemperatureMax());
        ps.setDouble(15, weather.getWindSpeed());
        ps.setString(16, weather.getWindAsWord().name());
      }

      @Override
      public int getBatchSize() {
        return built.size();
      }
    });

    List<Instant> forecastAts = built.stream().map(Weather::getForecastAt).toList();
    return weatherRepository.findAllByWeatherGridAndForecastedAtAndForecastAtInOrderByForecastAt(
        weatherGrid, forecastedAt, forecastAts);
  }

  private WindStrength toWindStrength(double speed) {
    if (speed < 4.0) {
      return WindStrength.WEAK;
    }
    if (speed < 9.0) {
      return WindStrength.MODERATE;
    }
    return WindStrength.STRONG;
  }
}