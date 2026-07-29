package com.sprint.mission.otboo.domain.weathernotification.weather.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeatherFetchReaderTest {

  @InjectMocks
  private WeatherFetchReader reader;

  @Mock
  private WeatherGridRepository weatherGridRepository;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(reader, "chunkSize", 2);
  }

  @Nested
  @DisplayName("Read")
  class Read {

    @Test
    @DisplayName("커서_기반으로_페이지_단위로_WeatherGrid를_순차_조회하고_소진되면_null을_반환한다")
    void 커서_기반으로_페이지_단위로_WeatherGrid를_순차_조회하고_소진되면_null을_반환한다() {
      // given
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      WeatherGrid grid2 = WeatherGrid.create(61, 127);
      given(weatherGridRepository.findPageByCursor(any(), any(), any()))
          .willReturn(List.of(grid1, grid2), List.of());

      // when
      WeatherGrid r1 = reader.read();
      WeatherGrid r2 = reader.read();
      WeatherGrid r3 = reader.read();

      // then
      assertThat(r1).isEqualTo(grid1);
      assertThat(r2).isEqualTo(grid2);
      assertThat(r3).isNull();
    }
  }
}