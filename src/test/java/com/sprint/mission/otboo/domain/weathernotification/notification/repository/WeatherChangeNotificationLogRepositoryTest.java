package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.WeatherChangeNotificationLog;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("WeatherChangeNotificationLogRepository")
class WeatherChangeNotificationLogRepositoryTest {

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Autowired
  private WeatherChangeNotificationLogRepository logRepository;

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private WeatherGrid weatherGrid() {
    WeatherGrid weatherGrid = ENTITY_FIXTURE_MONKEY.giveMeBuilder(WeatherGrid.class)
        .set("id", null)
        .sample();
    return weatherGridRepository.save(weatherGrid);
  }

  private WeatherChangeNotificationLog logOf(WeatherGrid weatherGrid, Instant forecastAt,
      Instant lastNotifiedForecastedAt) {
    return ENTITY_FIXTURE_MONKEY.giveMeBuilder(WeatherChangeNotificationLog.class)
        .set("id", null)
        .set("weatherGrid", weatherGrid)
        .set("forecastAt", forecastAt)
        .set("lastNotifiedForecastedAt", lastNotifiedForecastedAt)
        .sample();
  }

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("같은_weather_grid_id_forecast_at_조합은_유니크_제약_위반으로_저장할_수_없다")
    void 같은_weather_grid_id_forecast_at_조합은_유니크_제약_위반으로_저장할_수_없다() {
      WeatherGrid grid = weatherGrid();
      testEntityManager.flush();

      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");
      logRepository.save(logOf(grid, forecastAt, Instant.parse("2026-07-27T02:00:00Z")));
      testEntityManager.flush();

      WeatherChangeNotificationLog duplicate = logOf(grid, forecastAt,
          Instant.parse("2026-07-27T05:00:00Z"));

      assertThatThrownBy(() -> logRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }
}