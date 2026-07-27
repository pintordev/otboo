package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.global.config.JpaConfig;
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
class LocationRepositoryTest {

  @Autowired
  private LocationRepository locationRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("Location을 저장하면 ID가 생성되고 저장된 값을 조회할 수 있다")
    void save_and_findById() {
      Location location = Location.create(37.5674783, 126.9884121, 60, 127, List.of("서울특별시", "중구", "명동"));

      Location saved = locationRepository.save(location);
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<Location> found = locationRepository.findById(saved.getId());

      assertThat(found).isPresent();
      assertThat(found.get().getX()).isEqualTo(60);
      assertThat(found.get().getY()).isEqualTo(127);
      assertThat(found.get().getLocationNames()).containsExactly("서울특별시", "중구", "명동");
      assertThat(found.get().getCreatedAt()).isNotNull();
    }
  }
}