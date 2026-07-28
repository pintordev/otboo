package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Location;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.LocationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LocationWriterTest {

  @Mock
  private LocationRepository locationRepository;

  private LocationWriter locationWriter;

  @BeforeEach
  void setUp() {
    locationWriter = new LocationWriter(new ObjectMapper(), locationRepository);
  }

  @Nested
  @DisplayName("Save")
  class Save {

    @Test
    @DisplayName("위치명을_JSON으로_직렬화해서_삽입하고_재조회해서_반환한다")
    void 위치명을_JSON으로_직렬화해서_삽입하고_재조회해서_반환한다() {
      // given
      List<String> locationNames = List.of("서울특별시", "중구", "명동");
      Location saved = Location.create(83639, 227271, locationNames);
      given(locationRepository.findByLatBlockAndLonBlock(83639, 227271))
          .willReturn(Optional.of(saved));

      // when
      Location result = locationWriter.save(83639, 227271, locationNames);

      // then
      assertThat(result).isEqualTo(saved);
      verify(locationRepository).insertIfAbsent(any(), eq(83639), eq(227271),
          eq("[\"서울특별시\",\"중구\",\"명동\"]"));
    }
  }
}