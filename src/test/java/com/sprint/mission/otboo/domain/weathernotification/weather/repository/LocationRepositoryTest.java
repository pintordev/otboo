package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.global.config.JpaConfig;
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

    @Test
    @DisplayName("이미_존재하는_x_y_격자로_저장하면_무결성_제약_예외가_발생한다")
    void 이미_존재하는_x_y_격자로_저장하면_무결성_제약_예외가_발생한다() {
      Location location1 = Location.create(37.5674783, 126.9884121, 60, 127, List.of("서울특별시"));
      locationRepository.save(location1);
      testEntityManager.flush();

      Location location2 = Location.create(37.5674784, 126.9884122, 60, 127, List.of("서울특별시"));

      assertThatThrownBy(() -> locationRepository.saveAndFlush(location2))
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

      locationRepository.insertIfAbsent(firstId, 60, 127, 37.5674783, 126.9884121,
          "[\"서울특별시\"]");
      locationRepository.insertIfAbsent(secondId, 60, 127, 37.0, 127.0, "[\"다른값\"]");
      testEntityManager.flush();
      testEntityManager.clear();

      Optional<Location> found = locationRepository.findByXAndY(60, 127);

      assertThat(found).isPresent();
      assertThat(found.get().getId()).isEqualTo(firstId);
      assertThat(found.get().getLocationNames()).containsExactly("서울특별시");
    }
  }
}