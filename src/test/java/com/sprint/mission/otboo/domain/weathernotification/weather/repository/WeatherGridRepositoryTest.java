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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
class WeatherGridRepositoryTest {

  @Autowired
  private WeatherGridRepository weatherGridRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("WeatherGrid를_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다")
    void save_and_findById() {
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);

      WeatherGrid saved = weatherGridRepository.save(weatherGrid);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<WeatherGrid> found = weatherGridRepository.findById(saved.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getX()).isEqualTo(60);
      assertThat(found.get().getY()).isEqualTo(127);
      assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미_존재하는_x_y_격자로_저장하면_무결성_제약_예외가_발생한다")
    void 이미_존재하는_x_y_격자로_저장하면_무결성_제약_예외가_발생한다() {
      weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      WeatherGrid duplicate = WeatherGrid.create(60, 127);

      assertThatThrownBy(() -> weatherGridRepository.saveAndFlush(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("InsertIfAbsent")
  class InsertIfAbsent {

    @Test
    @DisplayName("같은_x_y로_두_번_삽입해도_먼저_삽입된_행만_유지된다")
    void 같은_x_y로_두_번_삽입해도_먼저_삽입된_행만_유지된다() {
      UUID firstId = UUID.randomUUID();
      UUID secondId = UUID.randomUUID();

      weatherGridRepository.insertIfAbsent(firstId, 60, 127);
      weatherGridRepository.insertIfAbsent(secondId, 60, 127);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<WeatherGrid> found = weatherGridRepository.findByXAndY(60, 127);

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(firstId);
    }
  }

  @Nested
  @DisplayName("FindPageByCursor")
  class FindPageByCursor {

    @Test
    @DisplayName("커서_이전_저장된_격자는_제외하고_createdAt_id_순으로_반환한다")
    void 커서_이전_저장된_격자는_제외하고_createdAt_id_순으로_반환한다() {
      WeatherGrid grid1 = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();
      WeatherGrid grid2 = weatherGridRepository.save(WeatherGrid.create(61, 127));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherGrid> firstPage = weatherGridRepository.findPageByCursor(
          Instant.EPOCH, new UUID(0L, 0L), 1);

      assertThat(firstPage).extracting(WeatherGrid::getId).containsExactly(grid1.getId());

      List<WeatherGrid> secondPage = weatherGridRepository.findPageByCursor(
          firstPage.get(0).getCreatedAt(), firstPage.get(0).getId(), 1);

      assertThat(secondPage).extracting(WeatherGrid::getId).containsExactly(grid2.getId());
    }

    @Test
    @DisplayName("더_이상_읽을_격자가_없으면_빈_리스트를_반환한다")
    void 더_이상_읽을_격자가_없으면_빈_리스트를_반환한다() {
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherGrid> page = weatherGridRepository.findPageByCursor(
          grid.getCreatedAt(), grid.getId(), 10);

      assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("limit이_0_이하이면_예외가_발생한다")
    void limit이_0_이하이면_예외가_발생한다() {
      // Spring Data 리포지토리 프록시가 IllegalArgumentException을
      // InvalidDataAccessApiUsageException으로 변환해서 던진다
      assertThatThrownBy(
          () -> weatherGridRepository.findPageByCursor(Instant.EPOCH, new UUID(0L, 0L), 0))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("FindPageByCursorExcludingForecasted")
  class FindPageByCursorExcludingForecasted {

    @Test
    @DisplayName("이번_forecastedAt에_이미_저장된_격자는_제외하고_반환한다")
    void 이번_forecastedAt에_이미_저장된_격자는_제외하고_반환한다() {
      WeatherGrid forecastedGrid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      WeatherGrid pendingGrid = weatherGridRepository.save(WeatherGrid.create(61, 128));
      testEntityManager.flush();

      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      testEntityManager.persist(Weather.create(forecastedGrid, forecastedAt,
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.5, WindStrength.WEAK, 28.0,
          PrecipitationType.NONE, 0.0, 0.0, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherGrid> page = weatherGridRepository.findPageByCursorExcludingForecasted(
          Instant.EPOCH, new UUID(0L, 0L), forecastedAt, 10);

      assertThat(page).extracting(WeatherGrid::getId).containsExactly(pendingGrid.getId());
    }

    @Test
    @DisplayName("다른_forecastedAt이면_이미_저장된_격자도_다시_반환한다")
    void 다른_forecastedAt이면_이미_저장된_격자도_다시_반환한다() {
      WeatherGrid grid = weatherGridRepository.save(WeatherGrid.create(60, 127));
      testEntityManager.flush();

      testEntityManager.persist(Weather.create(grid, Instant.parse("2026-07-27T08:00:00Z"),
          Instant.parse("2026-07-27T00:00:00Z"), SkyStatus.CLEAR, PrecipitationType.NONE, 0.0,
          0.0, 65.0, 0.0, 28.0, 0.0, 25.0, 31.0, 2.5, WindStrength.WEAK, 28.0,
          PrecipitationType.NONE, 0.0, 0.0, SkyStatus.CLEAR, PrecipitationType.NONE, 50.0));
      testEntityManager.flush();
      testEntityManager.clear();

      List<WeatherGrid> page = weatherGridRepository.findPageByCursorExcludingForecasted(
          Instant.EPOCH, new UUID(0L, 0L), Instant.parse("2026-07-27T11:00:00Z"), 10);

      assertThat(page).extracting(WeatherGrid::getId).containsExactly(grid.getId());
    }

    @Test
    @DisplayName("limit이_0_이하이면_예외가_발생한다")
    void limit이_0_이하이면_예외가_발생한다() {
      assertThatThrownBy(() -> weatherGridRepository.findPageByCursorExcludingForecasted(
          Instant.EPOCH, new UUID(0L, 0L), Instant.EPOCH, -1))
          .isInstanceOf(InvalidDataAccessApiUsageException.class)
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }
}