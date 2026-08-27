package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
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
import org.springframework.dao.InvalidDataAccessApiUsageException;
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
        WindStrength.WEAK, temperatureCurrent, PrecipitationType.NONE, 0.0, 0.0);
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
          WindStrength.WEAK,
          28.0,
          PrecipitationType.NONE,
          0.0,
          0.0
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
      assertThat(found.get().getBaselineTemperatureCurrent()).isEqualTo(28.0);
      assertThat(found.get().getBaselinePrecipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(found.get().getBaselinePrecipitationProbability()).isEqualTo(0.0);
      assertThat(found.get().getBaselinePrecipitationAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("같은_weather_grid_id_forecast_at_조합은_forecasted_at이_달라도_유니크_제약_위반으로_저장할_수_없다")
    void 같은_weather_grid_id_forecast_at_조합은_forecasted_at이_달라도_유니크_제약_위반으로_저장할_수_없다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");

      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T08:00:00Z"), forecastAt, 28.0));
      testEntityManager.flush();

      Weather duplicate = weatherOf(weatherGrid, Instant.parse("2026-07-27T11:00:00Z"),
          forecastAt, 29.0);

      assertThatThrownBy(() -> weatherRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("FindAllByWeatherGridAndForecastAtGreaterThanEqual")
  class FindAllByWeatherGridAndForecastAtGreaterThanEqual {

    @Test
    @DisplayName("from_이후_슬롯만_반환하고_이전_슬롯은_제외한다")
    void from_이후_슬롯만_반환하고_이전_슬롯은_제외한다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant from = Instant.parse("2026-07-27T00:00:00Z");
      Weather before = weatherRepository.save(weatherOf(weatherGrid,
          Instant.parse("2026-07-26T08:00:00Z"), Instant.parse("2026-07-26T23:00:00Z"), 20.0));
      Weather atFrom = weatherRepository.save(weatherOf(weatherGrid,
          Instant.parse("2026-07-27T08:00:00Z"), from, 24.0));
      Weather after = weatherRepository.save(weatherOf(weatherGrid,
          Instant.parse("2026-07-27T08:00:00Z"), Instant.parse("2026-07-27T01:00:00Z"), 25.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> result = weatherRepository.findAllByWeatherGridAndForecastAtGreaterThanEqual(
          weatherGrid, from);

      assertThat(result).extracting(Weather::getId)
          .containsExactlyInAnyOrder(atFrom.getId(), after.getId())
          .doesNotContain(before.getId());
    }
  }

  @Nested
  @DisplayName("FindAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan")
  class FindAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThan {

    @Test
    @DisplayName("여러_격자의_from_이상_to_미만_구간_슬롯을_한_번에_반환한다")
    void 여러_격자의_from_이상_to_미만_구간_슬롯을_한_번에_반환한다() {
      WeatherGrid targetGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid otherTargetGrid = weatherGridRepository.save(WeatherGrid.create(61, 128));
      WeatherGrid excludedGrid = weatherGridRepository.save(WeatherGrid.create(62, 129));
      testEntityManager.flush();

      Instant from = Instant.parse("2026-07-29T00:00:00Z");
      Instant to = Instant.parse("2026-07-30T00:00:00Z");
      Instant within = Instant.parse("2026-07-29T03:00:00Z");
      Weather matched1 = weatherRepository.save(
          weatherOf(targetGrid, Instant.parse("2026-07-27T08:00:00Z"), within, 20.0));
      Weather matched2 = weatherRepository.save(
          weatherOf(otherTargetGrid, Instant.parse("2026-07-27T08:00:00Z"), within, 21.0));
      weatherRepository.save(
          weatherOf(excludedGrid, Instant.parse("2026-07-27T08:00:00Z"), within, 22.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> result = weatherRepository
          .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
              List.of(targetGrid.getId(), otherTargetGrid.getId()), from, to);

      assertThat(result).extracting(Weather::getId)
          .containsExactlyInAnyOrder(matched1.getId(), matched2.getId());
    }

    @Test
    @DisplayName("결과를_forecastAt_오름차순으로_정렬해_반환한다")
    void 결과를_forecastAt_오름차순으로_정렬해_반환한다() {
      WeatherGrid gridA = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid gridB = weatherGridRepository.save(WeatherGrid.create(61, 128));
      testEntityManager.flush();

      Instant from = Instant.parse("2026-07-29T00:00:00Z");
      Instant to = Instant.parse("2026-07-30T00:00:00Z");
      Instant hour9 = Instant.parse("2026-07-29T09:00:00Z");
      Instant hour3 = Instant.parse("2026-07-29T03:00:00Z");
      Instant hour15 = Instant.parse("2026-07-29T15:00:00Z");
      // given - 저장 순서를 시각 순서와 다르게 뒤섞는다
      weatherRepository.save(weatherOf(gridA, Instant.parse("2026-07-27T08:00:00Z"), hour9, 20.0));
      weatherRepository.save(weatherOf(gridB, Instant.parse("2026-07-27T08:00:00Z"), hour3, 21.0));
      weatherRepository.save(weatherOf(gridA, Instant.parse("2026-07-27T08:00:00Z"), hour15, 22.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> result = weatherRepository
          .findAllByWeatherGridIdInAndForecastAtGreaterThanEqualAndForecastAtLessThanOrderByForecastAtAsc(
              List.of(gridA.getId(), gridB.getId()), from, to);

      assertThat(result).extracting(Weather::getForecastAt)
          .containsExactly(hour3, hour9, hour15);
    }
  }

  @Nested
  @DisplayName("FindGridsUpdatedAt")
  class FindGridsUpdatedAt {

    @Test
    @DisplayName("해당_forecastedAt으로_저장된_격자만_반환한다")
    void 해당_forecastedAt으로_저장된_격자만_반환한다() {
      WeatherGrid updatedGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid staleGrid = weatherGridRepository.save(WeatherGrid.create(61, 128));
      testEntityManager.flush();

      Instant thisRun = Instant.parse("2026-07-27T08:00:00Z");
      Instant previousRun = Instant.parse("2026-07-27T05:00:00Z");
      weatherRepository.save(
          weatherOf(updatedGrid, thisRun, Instant.parse("2026-07-27T00:00:00Z"), 25.0));
      weatherRepository.save(
          weatherOf(staleGrid, previousRun, Instant.parse("2026-07-27T00:00:00Z"), 21.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherGrid> result = weatherRepository.findGridsUpdatedAt(thisRun);

      assertThat(result).extracting(WeatherGrid::getId).containsExactly(updatedGrid.getId());
    }
  }

  @Nested
  @DisplayName("UpdateBaseline")
  class UpdateBaseline {

    @Test
    @DisplayName("baseline_컬럼만_갱신하고_current_컬럼은_그대로_둔다")
    void baseline_컬럼만_갱신하고_current_컬럼은_그대로_둔다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Weather weather = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T00:00:00Z"),
              Instant.parse("2026-07-27T02:00:00Z"), 20.0));
      testEntityManager.flush();
      testEntityManager.clear();

      weatherRepository.updateBaseline(weather.getId(), 25.0, PrecipitationType.RAIN, 40.0, 5.0);
      testEntityManager.clear();

      Weather reloaded = weatherRepository.findById(weather.getId()).orElseThrow();
      assertThat(reloaded.getBaselineTemperatureCurrent()).isEqualTo(25.0);
      assertThat(reloaded.getBaselinePrecipitationType()).isEqualTo(PrecipitationType.RAIN);
      assertThat(reloaded.getBaselinePrecipitationProbability()).isEqualTo(40.0);
      assertThat(reloaded.getBaselinePrecipitationAmount()).isEqualTo(5.0);
      assertThat(reloaded.getTemperatureCurrent()).isEqualTo(20.0);
    }
  }

  @Nested
  @DisplayName("FindForRetention")
  class FindForRetention {

    @Test
    @DisplayName("cutoff보다_forecastAt이_이전인_행만_반환하고_이후_행은_제외한다")
    void cutoff보다_forecastAt이_이전인_행만_반환하고_이후_행은_제외한다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant cutoff = Instant.parse("2026-07-20T00:00:00Z");
      Weather old = weatherRepository.save(weatherOf(weatherGrid,
          Instant.parse("2026-07-15T08:00:00Z"), Instant.parse("2026-07-15T00:00:00Z"), 20.0));
      weatherRepository.save(weatherOf(weatherGrid, Instant.parse("2026-07-21T08:00:00Z"),
          Instant.parse("2026-07-21T00:00:00Z"), 25.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherRetentionItem> result = weatherRepository.findForRetention(
          cutoff, Instant.EPOCH, new UUID(0L, 0L), 10);

      assertThat(result).extracting(WeatherRetentionItem::id).containsExactly(old.getId());
    }

    @Test
    @DisplayName("같은_forecastAt이면_id를_tie_breaker로_다음_페이지를_반환한다")
    void 같은_forecastAt이면_id를_tie_breaker로_다음_페이지를_반환한다() {
      WeatherGrid weatherGrid1 = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid weatherGrid2 = weatherGridRepository.save(WeatherGrid.create(61, 128));
      testEntityManager.flush();

      Instant cutoff = Instant.parse("2026-08-01T00:00:00Z");
      Instant sameForecastAt = Instant.parse("2026-07-15T00:00:00Z");
      weatherRepository.save(
          weatherOf(weatherGrid1, Instant.parse("2026-07-15T02:00:00Z"), sameForecastAt, 20.0));
      weatherRepository.save(
          weatherOf(weatherGrid2, Instant.parse("2026-07-15T05:00:00Z"), sameForecastAt, 21.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherRetentionItem> both = weatherRepository.findForRetention(
          cutoff, Instant.EPOCH, new UUID(0L, 0L), 2);
      assertThat(both).hasSize(2);
      UUID expectedFirstId = both.get(0).id();
      UUID expectedSecondId = both.get(1).id();

      List<WeatherRetentionItem> firstPage = weatherRepository.findForRetention(
          cutoff, Instant.EPOCH, new UUID(0L, 0L), 1);

      assertThat(firstPage).extracting(WeatherRetentionItem::id)
          .containsExactly(expectedFirstId);

      List<WeatherRetentionItem> secondPage = weatherRepository.findForRetention(
          cutoff, firstPage.get(0).forecastAt(), firstPage.get(0).id(), 1);

      assertThat(secondPage).extracting(WeatherRetentionItem::id)
          .containsExactly(expectedSecondId);
    }

    @Test
    @DisplayName("limit이_0_이하이면_예외가_발생한다")
    void limit이_0_이하이면_예외가_발생한다() {
      assertThatThrownBy(() -> weatherRepository.findForRetention(
          Instant.EPOCH, Instant.EPOCH, new UUID(0L, 0L), 0))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

}
