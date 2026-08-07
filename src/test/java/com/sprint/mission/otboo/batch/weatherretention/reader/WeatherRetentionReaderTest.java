package com.sprint.mission.otboo.batch.weatherretention.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.weatherretention.config.WeatherRetentionProperties;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

@ExtendWith(MockitoExtension.class)
class WeatherRetentionReaderTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Instant FIXED_NOW = Instant.parse("2026-08-07T03:00:00Z");

  private WeatherRetentionReader reader;

  @Mock
  private WeatherRepository weatherRepository;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    reader = new WeatherRetentionReader(weatherRepository, new WeatherRetentionProperties(2, 7),
        clock);
  }

  @Nested
  @DisplayName("Read")
  class Read {

    @Test
    @DisplayName("커서_기반으로_페이지_단위로_순차_조회하고_소진되면_null을_반환한다")
    void 커서_기반으로_페이지_단위로_순차_조회하고_소진되면_null을_반환한다() {
      // given
      WeatherRetentionItem item1 = new WeatherRetentionItem(UUID.randomUUID(),
          Instant.parse("2026-07-01T00:00:00Z"));
      WeatherRetentionItem item2 = new WeatherRetentionItem(UUID.randomUUID(),
          Instant.parse("2026-07-02T00:00:00Z"));
      given(weatherRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of(item1, item2), List.of());

      // when
      WeatherRetentionItem r1 = reader.read();
      WeatherRetentionItem r2 = reader.read();
      WeatherRetentionItem r3 = reader.read();

      // then
      assertThat(r1).isEqualTo(item1);
      assertThat(r2).isEqualTo(item2);
      assertThat(r3).isNull();
    }

    @Test
    @DisplayName("커서는_초기값에서_시작해서_직전_항목의_forecastAt_id_기준으로_다음_페이지를_조회한다")
    void 커서는_초기값에서_시작해서_직전_항목의_forecastAt_id_기준으로_다음_페이지를_조회한다() {
      // given
      WeatherRetentionItem item1 = new WeatherRetentionItem(UUID.randomUUID(),
          Instant.parse("2026-07-01T00:00:00Z"));
      WeatherRetentionItem item2 = new WeatherRetentionItem(UUID.randomUUID(),
          Instant.parse("2026-07-02T00:00:00Z"));
      WeatherRetentionItem item3 = new WeatherRetentionItem(UUID.randomUUID(),
          Instant.parse("2026-07-03T00:00:00Z"));
      given(weatherRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of(item1, item2), List.of(item3), List.of());

      // when
      reader.read();
      reader.read();
      reader.read();

      // then
      ArgumentCaptor<Instant> forecastAtCaptor = ArgumentCaptor.forClass(Instant.class);
      ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
      verify(weatherRepository, times(2))
          .findForRetention(any(), forecastAtCaptor.capture(), idCaptor.capture(), anyInt());

      List<Instant> capturedForecastAt = forecastAtCaptor.getAllValues();
      List<UUID> capturedIds = idCaptor.getAllValues();

      assertThat(capturedForecastAt.get(0)).isEqualTo(Instant.EPOCH);
      assertThat(capturedIds.get(0)).isEqualTo(new UUID(0L, 0L));

      assertThat(capturedForecastAt.get(1)).isEqualTo(item2.forecastAt());
      assertThat(capturedIds.get(1)).isEqualTo(item2.id());
    }

    @Test
    @DisplayName("cutoff는_retentionDays만큼_이전_KST_자정으로_계산해_넘긴다")
    void cutoff는_retentionDays만큼_이전_KST_자정으로_계산해_넘긴다() {
      // given
      given(weatherRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of());
      Instant expectedCutoff = LocalDate.now(Clock.fixed(FIXED_NOW, ZoneId.of("UTC")).withZone(KST))
          .minusDays(7)
          .atStartOfDay(KST)
          .toInstant();

      // when
      reader.read();

      // then
      verify(weatherRepository).findForRetention(eq(expectedCutoff), any(), any(), anyInt());
    }

    @Test
    @DisplayName("chunkSize를_limit으로_그대로_넘긴다")
    void chunkSize를_limit으로_그대로_넘긴다() {
      // given
      given(weatherRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      // when
      reader.read();

      // then
      verify(weatherRepository).findForRetention(any(), any(), any(), eq(2));
    }
  }
}