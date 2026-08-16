package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
class WeatherD1BaselineRepositoryTest {

  @Autowired
  private WeatherD1BaselineRepository weatherD1BaselineRepository;

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("저장하면_ID가_생성되고_hourly_snapshot이_그대로_조회된다")
    void 저장하면_ID가_생성되고_hourly_snapshot이_그대로_조회된다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant hour0 = Instant.parse("2026-07-29T00:00:00Z");
      Instant hour3 = Instant.parse("2026-07-29T03:00:00Z");
      Map<Instant, WeatherChangeSnapshot> hourlySnapshot = Map.of(
          hour0, new WeatherChangeSnapshot(20.0, PrecipitationType.NONE, 0.0, 0.0),
          hour3, new WeatherChangeSnapshot(18.5, PrecipitationType.RAIN, 60.0, 3.0));
      WeatherD1Baseline baseline = WeatherD1Baseline.create(weatherGrid,
          LocalDate.parse("2026-07-29"), hourlySnapshot, Instant.parse("2026-07-27T11:10:00Z"));

      WeatherD1Baseline saved = weatherD1BaselineRepository.save(baseline);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<WeatherD1Baseline> found = weatherD1BaselineRepository.findById(saved.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getWeatherGrid().getId()).isEqualTo(weatherGrid.getId());
      assertThat(found.get().getTargetDate()).isEqualTo(LocalDate.parse("2026-07-29"));
      assertThat(found.get().getHourlySnapshot()).isEqualTo(hourlySnapshot);
    }

    @Test
    @DisplayName("같은_weather_grid_id_target_date_조합은_유니크_제약_위반으로_저장할_수_없다")
    void 같은_weather_grid_id_target_date_조합은_유니크_제약_위반으로_저장할_수_없다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      LocalDate targetDate = LocalDate.parse("2026-07-29");
      weatherD1BaselineRepository.save(WeatherD1Baseline.create(weatherGrid, targetDate,
          Map.of(), Instant.parse("2026-07-27T11:10:00Z")));
      testEntityManager.flush();

      WeatherD1Baseline duplicate = WeatherD1Baseline.create(weatherGrid, targetDate, Map.of(),
          Instant.parse("2026-07-27T11:20:00Z"));

      assertThatThrownBy(() -> weatherD1BaselineRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }
}