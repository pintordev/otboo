package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class WeatherRepositoryTest {

  @Autowired
  private WeatherRepository weatherRepository;

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private Weather weatherOf(WeatherGrid weatherGrid, Instant forecastedAt, Instant forecastAt,
      double temperatureCurrent) {
    return Weather.create(weatherGrid, forecastedAt, forecastAt, SkyStatus.CLEAR,
        PrecipitationType.NONE, 0.0, 0.0, 65.0, 0.0, temperatureCurrent, 0.0, 25.0, 31.0, 2.5,
        WindStrength.WEAK);
  }

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

    @Test
    @DisplayName("같은_weather_grid_id_forecast_at_forecasted_at_조합은_유니크_제약_위반으로_저장할_수_없다")
    void 같은_weather_grid_id_forecast_at_forecasted_at_조합은_유니크_제약_위반으로_저장할_수_없다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");

      weatherRepository.save(weatherOf(weatherGrid, forecastedAt, forecastAt, 28.0));
      testEntityManager.flush();

      Weather duplicate = weatherOf(weatherGrid, forecastedAt, forecastAt, 29.0);

      assertThatThrownBy(() -> weatherRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
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
  }

  @Nested
  @DisplayName("InsertIfAbsent")
  class InsertIfAbsent {

    @Test
    @DisplayName("신규_조합이면_1행_insert되고_findByWeatherGridAndForecastAtAndForecastedAt으로_조회된다")
    void 신규_조합이면_1행_insert되고_findByWeatherGridAndForecastAtAndForecastedAt으로_조회된다() {
      // given
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");
      UUID id = UUID.randomUUID();

      // when
      int inserted = insertIfAbsent(id, weatherGrid, forecastedAt, forecastAt, 28.0);
      testEntityManager.clear();

      // then
      assertThat(inserted).isEqualTo(1);
      Optional<Weather> found = weatherRepository
          .findByWeatherGridAndForecastAtAndForecastedAt(weatherGrid, forecastAt, forecastedAt);
      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(id);
      assertThat(found.get().getTemperatureCurrent()).isEqualTo(28.0);
    }

    @Test
    @DisplayName("이미_존재하는_조합이면_0을_반환하고_기존_행이_그대로_유지된다")
    void 이미_존재하는_조합이면_0을_반환하고_기존_행이_그대로_유지된다() {
      // given
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");
      UUID firstId = UUID.randomUUID();
      insertIfAbsent(firstId, weatherGrid, forecastedAt, forecastAt, 28.0);
      testEntityManager.clear();

      // when - 같은 조합, 다른 id/값으로 재시도
      int inserted = insertIfAbsent(UUID.randomUUID(), weatherGrid, forecastedAt, forecastAt,
          99.0);
      testEntityManager.clear();

      // then
      assertThat(inserted).isEqualTo(0);
      Optional<Weather> found = weatherRepository
          .findByWeatherGridAndForecastAtAndForecastedAt(weatherGrid, forecastAt, forecastedAt);
      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(firstId);
      assertThat(found.get().getTemperatureCurrent()).isEqualTo(28.0);
    }

    @Test
    @DisplayName("호출_후_이전에_로딩해둔_관리_엔티티가_영속성_컨텍스트에서_분리된다")
    void 호출_후_이전에_로딩해둔_관리_엔티티가_영속성_컨텍스트에서_분리된다() {
      // given
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Weather existing = weatherRepository.save(weatherOf(weatherGrid,
          Instant.parse("2026-07-27T05:00:00Z"), Instant.parse("2026-07-27T00:00:00Z"), 20.0));
      testEntityManager.flush();

      // 관리(managed) 상태로 재조회해 영속성 컨텍스트에 올려둔다
      Weather managed = weatherRepository.findById(existing.getId()).orElseThrow();
      assertThat(testEntityManager.getEntityManager().contains(managed)).isTrue();

      // when
      insertIfAbsent(UUID.randomUUID(), weatherGrid, Instant.parse("2026-07-27T08:00:00Z"),
          Instant.parse("2026-07-27T00:00:00Z"), 28.0);

      // then - clearAutomatically=true라면 native INSERT 실행 후 영속성 컨텍스트가 비워져
      // 이전에 로딩해둔 엔티티는 더 이상 관리 상태가 아니다. 이 native INSERT를 다른 곳에서
      // 재사용할 때, 그 시점 영속성 컨텍스트에 남아있던 stale 엔티티로 인한 불일치를 막는다
      assertThat(testEntityManager.getEntityManager().contains(managed)).isFalse();
    }

    private int insertIfAbsent(UUID id, WeatherGrid weatherGrid, Instant forecastedAt,
        Instant forecastAt, double temperatureCurrent) {
      return weatherRepository.insertIfAbsent(id, weatherGrid.getId(), forecastedAt, forecastAt,
          SkyStatus.CLEAR.name(), PrecipitationType.NONE.name(), 0.0, 0.0, 65.0, 0.0,
          temperatureCurrent, 0.0, 25.0, 31.0, 2.5, WindStrength.WEAK.name());
    }
  }
}
