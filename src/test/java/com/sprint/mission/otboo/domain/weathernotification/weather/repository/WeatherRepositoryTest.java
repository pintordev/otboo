package com.sprint.mission.otboo.domain.weathernotification.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
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
  private LocationRepository locationRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("Weather를_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다")
    void Weather를_저장하면_ID가_생성되고_저장된_값을_조회할_수_있다() {
      Location location = locationRepository.save(
          Location.create(37.5674783, 126.9884121, 60, 127, List.of("서울특별시")));
      testEntityManager.flush();

      Weather weather = Weather.create(
          location,
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
      assertThat(found.get().getLocation().getId()).isEqualTo(location.getId());
      assertThat(found.get().getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(found.get().getTemperatureCurrent()).isEqualTo(28.0);
      assertThat(found.get().getCreatedAt()).isNotNull();
    }
  }
}
