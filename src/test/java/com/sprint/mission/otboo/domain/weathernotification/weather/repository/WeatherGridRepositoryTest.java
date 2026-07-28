package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.global.config.JpaConfig;
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
@Import(JpaConfig.class)
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
}