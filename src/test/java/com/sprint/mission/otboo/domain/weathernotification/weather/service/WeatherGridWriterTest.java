package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherGridWriterTest {

  @Mock
  private WeatherGridRepository weatherGridRepository;

  private WeatherGridWriter weatherGridWriter;

  @BeforeEach
  void setUp() {
    weatherGridWriter = new WeatherGridWriter(weatherGridRepository);
  }

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("격자를_삽입하고_재조회해서_반환한다")
    void 격자를_삽입하고_재조회해서_반환한다() {
      // given
      WeatherGrid saved = WeatherGrid.create(60, 127);
      given(weatherGridRepository.findByXAndY(60, 127)).willReturn(Optional.of(saved));

      // when
      WeatherGrid result = weatherGridWriter.save(60, 127);

      // then
      assertThat(result).isEqualTo(saved);
      verify(weatherGridRepository).insertIfAbsent(any(), eq(60), eq(127));
    }
  }
}