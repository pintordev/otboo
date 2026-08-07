package com.sprint.mission.otboo.batch.weatherfetch.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherFetchProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherGridRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WeatherFetchReaderTest {

  private static final BaseTime BASE_TIME = new BaseTime("20260727", "1700");

  private WeatherFetchReader reader;

  @Mock
  private WeatherGridRepository weatherGridRepository;

  @BeforeEach
  void setUp() {
    // JobExecutionContext에서 SpEL로 주입받는 값을 생성자 인자로 직접 대신한다
    reader = new WeatherFetchReader(weatherGridRepository, new WeatherFetchProperties(2, 10, 3),
        BASE_TIME.baseDate(), BASE_TIME.baseTime());
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
      given(weatherGridRepository.findPageByCursorExcludingForecasted(any(), any(), any(), anyInt()))
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

    @Test
    @DisplayName("커서는_초기값에서_시작해서_직전_항목의_createdAt_id_기준으로_다음_페이지를_조회한다")
    void 커서는_초기값에서_시작해서_직전_항목의_createdAt_id_기준으로_다음_페이지를_조회한다() {
      // given
      WeatherGrid grid1 = WeatherGrid.create(60, 127);
      Instant grid1CreatedAt = Instant.parse("2026-07-27T00:00:00Z");
      UUID grid1Id = UUID.randomUUID();
      ReflectionTestUtils.setField(grid1, "createdAt", grid1CreatedAt);
      ReflectionTestUtils.setField(grid1, "id", grid1Id);

      WeatherGrid grid2 = WeatherGrid.create(61, 127);
      Instant grid2CreatedAt = Instant.parse("2026-07-27T00:01:00Z");
      UUID grid2Id = UUID.randomUUID();
      ReflectionTestUtils.setField(grid2, "createdAt", grid2CreatedAt);
      ReflectionTestUtils.setField(grid2, "id", grid2Id);

      WeatherGrid grid3 = WeatherGrid.create(62, 127);

      given(weatherGridRepository.findPageByCursorExcludingForecasted(any(), any(), any(), anyInt()))
          .willReturn(List.of(grid1, grid2), List.of(grid3), List.of());

      // when
      reader.read();
      reader.read();
      reader.read();

      // then
      ArgumentCaptor<Instant> createdAtCaptor = ArgumentCaptor.forClass(Instant.class);
      ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
      verify(weatherGridRepository, times(2))
          .findPageByCursorExcludingForecasted(createdAtCaptor.capture(), idCaptor.capture(),
              any(), anyInt());

      List<Instant> capturedCreatedAt = createdAtCaptor.getAllValues();
      List<UUID> capturedIds = idCaptor.getAllValues();

      assertThat(capturedCreatedAt.get(0)).isEqualTo(Instant.EPOCH);
      assertThat(capturedIds.get(0)).isEqualTo(new UUID(0L, 0L));

      assertThat(capturedCreatedAt.get(1)).isEqualTo(grid2CreatedAt);
      assertThat(capturedIds.get(1)).isEqualTo(grid2Id);
    }

    @Test
    @DisplayName("JobExecutionContext에서_주입받은_baseTime을_forecastedAt으로_넘겨_이미_저장된_격자를_제외한다")
    void JobExecutionContext에서_주입받은_baseTime을_forecastedAt으로_넘겨_이미_저장된_격자를_제외한다() {
      // given
      given(weatherGridRepository.findPageByCursorExcludingForecasted(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      // when
      reader.read();

      // then
      verify(weatherGridRepository).findPageByCursorExcludingForecasted(any(), any(),
          eq(BASE_TIME.toInstant()), anyInt());
    }
  }
}