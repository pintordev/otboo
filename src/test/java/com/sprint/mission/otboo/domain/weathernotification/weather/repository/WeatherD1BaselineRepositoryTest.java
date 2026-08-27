package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherD1Baseline;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
      WeatherD1Baseline actual = found.orElseThrow();
      assertThat(actual.getWeatherGrid().getId()).isEqualTo(weatherGrid.getId());
      assertThat(actual.getTargetDate()).isEqualTo(LocalDate.parse("2026-07-29"));
      assertThat(actual.getHourlySnapshot()).isEqualTo(hourlySnapshot);
      assertThat(actual.getCreatedAt()).isNotNull();
      assertThat(actual.getUpdatedAt()).isNotNull();
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

  @Nested
  @DisplayName("FindForRetention")
  class FindForRetention {

    @Test
    @DisplayName("cutoff_이하인_target_date만_반환하고_이후_날짜는_제외한다")
    void cutoff_이하인_target_date만_반환하고_이후_날짜는_제외한다() {
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      LocalDate cutoff = LocalDate.parse("2026-07-28");
      WeatherD1Baseline overdue = weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          grid, LocalDate.parse("2026-07-28"), Map.of(), Instant.parse("2026-07-26T11:10:00Z")));
      weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          grid, LocalDate.parse("2026-07-29"), Map.of(), Instant.parse("2026-07-27T11:10:00Z")));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherD1BaselineRetentionItem> result = weatherD1BaselineRepository
          .findForRetention(cutoff, LocalDate.EPOCH, new UUID(0L, 0L), 10);

      assertThat(result).extracting(WeatherD1BaselineRetentionItem::id)
          .containsExactly(overdue.getId());
    }

    @Test
    @DisplayName("같은_target_date면_id를_tie_breaker로_다음_페이지를_반환한다")
    void 같은_target_date면_id를_tie_breaker로_다음_페이지를_반환한다() {
      WeatherGrid grid1 = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid grid2 = weatherGridRepository.save(WeatherGrid.create(61, 128));
      testEntityManager.flush();

      LocalDate cutoff = LocalDate.parse("2026-08-01");
      LocalDate sameTargetDate = LocalDate.parse("2026-07-28");
      weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          grid1, sameTargetDate, Map.of(), Instant.parse("2026-07-26T11:10:00Z")));
      weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          grid2, sameTargetDate, Map.of(), Instant.parse("2026-07-26T11:10:00Z")));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherD1BaselineRetentionItem> both = weatherD1BaselineRepository
          .findForRetention(cutoff, LocalDate.EPOCH, new UUID(0L, 0L), 2);
      assertThat(both).hasSize(2);
      UUID expectedFirstId = both.get(0).id();
      UUID expectedSecondId = both.get(1).id();

      List<WeatherD1BaselineRetentionItem> firstPage = weatherD1BaselineRepository
          .findForRetention(cutoff, LocalDate.EPOCH, new UUID(0L, 0L), 1);

      assertThat(firstPage).extracting(WeatherD1BaselineRetentionItem::id)
          .containsExactly(expectedFirstId);

      List<WeatherD1BaselineRetentionItem> secondPage = weatherD1BaselineRepository
          .findForRetention(cutoff, firstPage.get(0).targetDate(), firstPage.get(0).id(), 1);

      assertThat(secondPage).extracting(WeatherD1BaselineRetentionItem::id)
          .containsExactly(expectedSecondId);
    }

    @Test
    @DisplayName("limit이_0_이하이면_예외가_발생한다")
    void limit이_0_이하이면_예외가_발생한다() {
      assertThatThrownBy(() -> weatherD1BaselineRepository.findForRetention(
          LocalDate.EPOCH, LocalDate.EPOCH, new UUID(0L, 0L), 0))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("FindAllByWeatherGridIdInAndTargetDate")
  class FindAllByWeatherGridIdInAndTargetDate {

    @Test
    @DisplayName("여러_격자의_같은_target_date_baseline을_한_번에_반환한다")
    void 여러_격자의_같은_target_date_baseline을_한_번에_반환한다() {
      WeatherGrid targetGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid otherTargetGrid = weatherGridRepository.save(WeatherGrid.create(61, 128));
      WeatherGrid excludedGrid = weatherGridRepository.save(WeatherGrid.create(62, 129));
      testEntityManager.flush();

      LocalDate targetDate = LocalDate.parse("2026-07-29");
      WeatherD1Baseline matched1 = weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          targetGrid, targetDate, Map.of(), Instant.parse("2026-07-27T11:10:00Z")));
      WeatherD1Baseline matched2 = weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          otherTargetGrid, targetDate, Map.of(), Instant.parse("2026-07-27T11:10:00Z")));
      weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          excludedGrid, targetDate, Map.of(), Instant.parse("2026-07-27T11:10:00Z")));
      weatherD1BaselineRepository.save(WeatherD1Baseline.create(
          targetGrid, targetDate.plusDays(1), Map.of(), Instant.parse("2026-07-27T11:10:00Z")));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherD1Baseline> result = weatherD1BaselineRepository
          .findAllByWeatherGridIdInAndTargetDate(
              List.of(targetGrid.getId(), otherTargetGrid.getId()), targetDate);

      assertThat(result).extracting(WeatherD1Baseline::getId)
          .containsExactlyInAnyOrder(matched1.getId(), matched2.getId());
    }
  }
}