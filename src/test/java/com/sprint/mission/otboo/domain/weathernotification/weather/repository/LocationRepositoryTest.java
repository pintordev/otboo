package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
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
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
class LocationRepositoryTest {

  @Autowired
  private LocationRepository locationRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("Location을_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다")
    void save_and_findById() {
      Location location = Location.create(83639, 227271, List.of("서울특별시", "중구", "명동"));

      Location saved = locationRepository.save(location);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<Location> found = locationRepository.findById(saved.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getLatBlock()).isEqualTo(83639);
      assertThat(found.get().getLonBlock()).isEqualTo(227271);
      assertThat(found.get().getLocationNames()).containsExactly("서울특별시", "중구", "명동");
      assertThat(found.get().getCreatedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("InsertIfAbsent")
  class InsertIfAbsent {

    @Test
    @DisplayName("같은_블록으로_두_번_삽입해도_먼저_삽입된_행만_유지된다")
    void 같은_블록으로_두_번_삽입해도_먼저_삽입된_행만_유지된다() {
      UUID firstId = UUID.randomUUID();
      UUID secondId = UUID.randomUUID();

      locationRepository.insertIfAbsent(firstId, 83639, 227271, "[\"서울특별시\"]");
      locationRepository.insertIfAbsent(secondId, 83639, 227271, "[\"다른값\"]");
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<Location> found = locationRepository.findByLatBlockAndLonBlock(83639, 227271);

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(firstId);
      assertThat(found.get().getLocationNames()).containsExactly("서울특별시");
    }
  }
}