package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

  // saveSlots()가 쓰는 upsert - baseline_*은 DO UPDATE SET 목록에 없다. PostgreSQL은
  // 명시 안 된 컬럼을 건드리지 않으므로, 슬롯이 최초 INSERT될 때 넣은 baseline이 이후 몇 번을
  // 갱신하든 그대로 남는다.
  private static final String UPSERT_SQL = """
      INSERT INTO weathers (id, weather_grid_id, forecasted_at, forecast_at, sky_status,
          precipitation_type, precipitation_amount, precipitation_probability,
          humidity_current, humidity_compared, temperature_current, temperature_compared,
          temperature_min, temperature_max, wind_speed, wind_as_word,
          baseline_temperature_current, baseline_precipitation_type,
          baseline_precipitation_probability, baseline_precipitation_amount,
          sky_status_worst, precipitation_type_mode, humidity_max, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
      ON CONFLICT (weather_grid_id, forecast_at) DO UPDATE SET
          forecasted_at = EXCLUDED.forecasted_at,
          sky_status = EXCLUDED.sky_status,
          precipitation_type = EXCLUDED.precipitation_type,
          precipitation_amount = EXCLUDED.precipitation_amount,
          precipitation_probability = EXCLUDED.precipitation_probability,
          humidity_current = EXCLUDED.humidity_current,
          humidity_compared = EXCLUDED.humidity_compared,
          temperature_current = EXCLUDED.temperature_current,
          temperature_compared = EXCLUDED.temperature_compared,
          temperature_min = EXCLUDED.temperature_min,
          temperature_max = EXCLUDED.temperature_max,
          wind_speed = EXCLUDED.wind_speed,
          wind_as_word = EXCLUDED.wind_as_word,
          sky_status_worst = EXCLUDED.sky_status_worst,
          precipitation_type_mode = EXCLUDED.precipitation_type_mode,
          humidity_max = EXCLUDED.humidity_max
      WHERE weathers.forecasted_at < EXCLUDED.forecasted_at
      """;

  private final WeatherRepository weatherRepository;
  private final JdbcTemplate jdbcTemplate;

  // 슬롯 단위 변환 - 날짜별 하루 1건이 아니라 슬롯(날짜+시각)마다 1건을 만든다.
  // "어제 대비"도 날짜 단위 누적이 아니라 슬롯 단위로 정확히 24시간 전 같은 슬롯과 비교한다.
  public List<Weather> buildSlots(WeatherGrid weatherGrid, Instant forecastedAt,
      List<WeatherForecastSlotDto> slotForecasts, Map<Instant, Weather> existingBySlot) {
    List<Weather> built = new ArrayList<>();
    for (WeatherForecastSlotDto dto : slotForecasts) {
      Weather sameSlotYesterday = existingBySlot.get(dto.slotAt().minus(1, ChronoUnit.DAYS));
      Double temperatureCompared = sameSlotYesterday != null
          ? dto.temperatureCurrent() - sameSlotYesterday.getTemperatureCurrent() : null;
      Double humidityCompared = sameSlotYesterday != null
          ? dto.humidityCurrent() - sameSlotYesterday.getHumidityCurrent() : null;

      built.add(Weather.create(weatherGrid, forecastedAt, dto.slotAt(), dto.skyStatus(),
          dto.precipitationType(), dto.precipitationAmount(), dto.precipitationProbability(),
          dto.humidityCurrent(), humidityCompared, dto.temperatureCurrent(), temperatureCompared,
          dto.temperatureMin(), dto.temperatureMax(), dto.windSpeed(),
          toWindStrength(dto.windSpeed()),
          // baseline_* - 매번 현재 값 그대로 전달. saveSlots()의 upsert SQL이 baseline_*을
          // SET 목록에서 뺀 뒤부터는 이미 존재하는 슬롯 갱신 시 이 인자가 무시되고 기존
          // baseline이 유지된다 - 최초 삽입일 때만 실제로 반영된다.
          dto.temperatureCurrent(), dto.precipitationType(), dto.precipitationProbability(),
          dto.precipitationAmount(), dto.skyStatusWorst(), dto.precipitationTypeMode(),
          dto.humidityMax()));
    }
    return built;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Weather> saveSlots(WeatherGrid weatherGrid, Instant forecastedAt,
      List<WeatherForecastSlotDto> slotForecasts, Map<Instant, Weather> existingBySlot) {
    List<Weather> built = buildSlots(weatherGrid, forecastedAt, slotForecasts, existingBySlot);
    if (built.isEmpty()) {
      return built;
    }

    jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
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
        ps.setObject(10, weather.getHumidityCompared(), Types.DOUBLE);
        ps.setDouble(11, weather.getTemperatureCurrent());
        ps.setObject(12, weather.getTemperatureCompared(), Types.DOUBLE);
        ps.setDouble(13, weather.getTemperatureMin());
        ps.setDouble(14, weather.getTemperatureMax());
        ps.setDouble(15, weather.getWindSpeed());
        ps.setString(16, weather.getWindAsWord().name());
        ps.setObject(17, weather.getBaselineTemperatureCurrent(), Types.DOUBLE);
        ps.setString(18, weather.getBaselinePrecipitationType() == null ? null
            : weather.getBaselinePrecipitationType().name());
        ps.setObject(19, weather.getBaselinePrecipitationProbability(), Types.DOUBLE);
        ps.setObject(20, weather.getBaselinePrecipitationAmount(), Types.DOUBLE);
        ps.setString(21, weather.getSkyStatusWorst() == null ? null
            : weather.getSkyStatusWorst().name());
        ps.setString(22, weather.getPrecipitationTypeMode() == null ? null
            : weather.getPrecipitationTypeMode().name());
        ps.setObject(23, weather.getHumidityMax(), Types.DOUBLE);
      }

      @Override
      public int getBatchSize() {
        return built.size();
      }
    });

    List<Instant> forecastAts = built.stream().map(Weather::getForecastAt).toList();
    return weatherRepository.findAllByWeatherGridAndForecastAtInOrderByForecastAt(
        weatherGrid, forecastAts);
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