package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.global.config.JpaConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class WeatherRepositoryTest {

  @Autowired
  private WeatherRepository weatherRepository;

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("Weather를_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다")
    void Weather를_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Weather weather = Weather.create(
          weatherGrid,
          Instant.parse("2026-07-27T08:00:00Z"),
          Instant.parse("2026-07-27T00:00:00Z"),
          SkyStatus.CLEAR,
          PrecipitationType.NONE,
          0.0,
          0.0,
          65.0,
          0.0,
          28.0,
          0.0,
          25.0,
          31.0,
          2.5,
          WindStrength.WEAK
      );

      Weather saved = weatherRepository.save(weather);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<Weather> found = weatherRepository.findById(saved.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getWeatherGrid().getId()).isEqualTo(weatherGrid.getId());
      assertThat(found.get().getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(found.get().getTemperatureCurrent()).isEqualTo(28.0);
      assertThat(found.get().getCreatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("FindLatestRevisions")
  class FindLatestRevisions {

    @Test
    @DisplayName("같은_forecastAt에_여러_revision이_있으면_가장_최근_forecastedAt_행만_반환한다")
    void 같은_forecastAt에_여러_revision이_있으면_가장_최근_forecastedAt_행만_반환한다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant day1 = Instant.parse("2026-07-27T00:00:00Z");
      Instant day2 = Instant.parse("2026-07-28T00:00:00Z");

      Weather day1OldRevision = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T02:10:00Z"), day1, 25.0));
      Weather day1NewRevision = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T17:10:00Z"), day1, 28.0));
      Weather day2Revision = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T17:10:00Z"), day2, 27.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> latestRevisions = weatherRepository.findLatestRevisions(weatherGrid, day1);

      assertThat(latestRevisions).hasSize(2);
      assertThat(latestRevisions).extracting(Weather::getId)
          .containsExactlyInAnyOrder(day1NewRevision.getId(), day2Revision.getId())
          .doesNotContain(day1OldRevision.getId());
    }

    private Weather weatherOf(WeatherGrid weatherGrid, Instant forecastedAt, Instant forecastAt,
        double temperatureCurrent) {
      return Weather.create(weatherGrid, forecastedAt, forecastAt, SkyStatus.CLEAR,
          PrecipitationType.NONE, 0.0, 0.0, 65.0, 0.0, temperatureCurrent, 0.0, 25.0, 31.0, 2.5,
          WindStrength.WEAK);
    }
  }
}
