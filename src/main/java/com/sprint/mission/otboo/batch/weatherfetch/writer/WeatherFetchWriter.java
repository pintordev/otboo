package com.sprint.mission.otboo.batch.weatherfetch.writer;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherFetchWriter implements ItemWriter<List<Weather>> {

  // 건당 개별 insert 왕복과 매 건 flush를 없애기 위해 JDBC 배치로 저장한다. 멱등성은
  // ON CONFLICT DO NOTHING으로 유지한다.
  private static final String INSERT_SQL = """
      INSERT INTO weathers (id, weather_grid_id, forecasted_at, forecast_at, sky_status,
          precipitation_type, precipitation_amount, precipitation_probability,
          humidity_current, humidity_compared, temperature_current, temperature_compared,
          temperature_min, temperature_max, wind_speed, wind_as_word, created_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
      ON CONFLICT (weather_grid_id, forecast_at, forecasted_at) DO NOTHING
      """;

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void write(Chunk<? extends List<Weather>> chunk) {
    List<Weather> all = chunk.getItems().stream().flatMap(List::stream).toList();
    if (all.isEmpty()) {
      log.info("WeatherFetchWriter chunk 저장 완료: 격자수={}, 시도=0, 삽입=0", chunk.size());
      return;
    }

    int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        Weather weather = all.get(i);
        ps.setObject(1, UUID.randomUUID());
        ps.setObject(2, weather.getWeatherGrid().getId());
        ps.setTimestamp(3, Timestamp.from(weather.getForecastedAt()));
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
      }

      @Override
      public int getBatchSize() {
        return all.size();
      }
    });

    // pgjdbc가 배치를 재작성하면 개별 영향 행 수 대신 Statement.SUCCESS_NO_INFO(-2)를 반환할 수
    // 있다 - 이 경우는 실패가 아니라 "insert는 됐는데 정확한 건수를 모른다"는 뜻이라 삽입 건수에서
    // 빠뜨리지 않고 별도로 집계한다.
    long insertedCount = Arrays.stream(results).filter(result -> result > 0).count();
    long unknownCount = Arrays.stream(results)
        .filter(result -> result == Statement.SUCCESS_NO_INFO)
        .count();
    log.info("WeatherFetchWriter chunk 저장 완료: 격자수={}, 시도={}, 삽입={}, 결과불명={}",
        chunk.size(), all.size(), insertedCount, unknownCount);
  }
}