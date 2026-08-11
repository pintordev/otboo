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
  @DisplayName("FindRecentTwoRevisions")
  class FindRecentTwoRevisions {

    @Test
    @DisplayName("같은_forecastAt에_3개_리비전이_있어도_최신_2개만_반환한다")
    void 같은_forecastAt에_3개_리비전이_있어도_최신_2개만_반환한다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");
      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T02:10:00Z"), forecastAt, 20.0));
      Weather middle = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T05:10:00Z"), forecastAt, 22.0));
      Weather latest = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T08:10:00Z"), forecastAt, 25.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> result = weatherRepository.findRecentTwoRevisions(weatherGrid,
          List.of(forecastAt));

      assertThat(result).hasSize(2);
      assertThat(result).extracting(Weather::getId)
          .containsExactlyInAnyOrder(middle.getId(), latest.getId());
    }

    @Test
    @DisplayName("대상_리스트에_없는_forecastAt은_제외된다")
    void 대상_리스트에_없는_forecastAt은_제외된다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant d0 = Instant.parse("2026-07-27T00:00:00Z");
      Instant d1 = Instant.parse("2026-07-28T00:00:00Z");
      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T02:10:00Z"), d0, 20.0));
      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T05:10:00Z"), d0, 22.0));
      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T05:10:00Z"), d1, 18.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> result = weatherRepository.findRecentTwoRevisions(weatherGrid, List.of(d0));

      assertThat(result).extracting(Weather::getForecastAt).containsOnly(d0);
    }

    @Test
    @DisplayName("직전_리비전이_없으면_1건만_반환한다")
    void 직전_리비전이_없으면_1건만_반환한다() {
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant forecastAt = Instant.parse("2026-07-27T00:00:00Z");
      Weather only = weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-27T02:10:00Z"), forecastAt, 20.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<Weather> result = weatherRepository.findRecentTwoRevisions(weatherGrid,
          List.of(forecastAt));

      assertThat(result).extracting(Weather::getId).containsExactly(only.getId());
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
      WeatherGrid weatherGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      Instant cutoff = Instant.parse("2026-08-01T00:00:00Z");
      Instant sameForecastAt = Instant.parse("2026-07-15T00:00:00Z");
      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-15T02:00:00Z"), sameForecastAt, 20.0));
      weatherRepository.save(
          weatherOf(weatherGrid, Instant.parse("2026-07-15T05:00:00Z"), sameForecastAt, 21.0));
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
