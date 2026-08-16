package com.sprint.mission.otboo.batch.weatherretention.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.weatherretention.config.WeatherRetentionProperties;
import com.sprint.mission.otboo.batch.weatherretention.dto.WeatherD1BaselineRetentionItem;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherD1BaselineRepository;
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
class WeatherD1BaselineRetentionReaderTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  // 2026-08-07 12:00 KST
  private static final Instant FIXED_NOW = Instant.parse("2026-08-07T03:00:00Z");

  private WeatherD1BaselineRetentionReader reader;

  @Mock
  private WeatherD1BaselineRepository weatherD1BaselineRepository;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    reader = new WeatherD1BaselineRetentionReader(weatherD1BaselineRepository,
        new WeatherRetentionProperties(2, 7), clock);
  }

  @Nested
  @DisplayName("Read")
  class Read {

    @Test
    @DisplayName("커서_기반으로_페이지_단위로_순차_조회하고_소진되면_null을_반환한다")
    void 커서_기반으로_페이지_단위로_순차_조회하고_소진되면_null을_반환한다() {
      // given
      WeatherD1BaselineRetentionItem item1 = new WeatherD1BaselineRetentionItem(
          UUID.randomUUID(), LocalDate.parse("2026-07-01"));
      WeatherD1BaselineRetentionItem item2 = new WeatherD1BaselineRetentionItem(
          UUID.randomUUID(), LocalDate.parse("2026-07-02"));
      given(weatherD1BaselineRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of(item1, item2), List.of());

      // when
      WeatherD1BaselineRetentionItem r1 = reader.read();
      WeatherD1BaselineRetentionItem r2 = reader.read();
      WeatherD1BaselineRetentionItem r3 = reader.read();

      // then
      assertThat(r1).isEqualTo(item1);
      assertThat(r2).isEqualTo(item2);
      assertThat(r3).isNull();
    }

    @Test
    @DisplayName("커서는_초기값에서_시작해서_직전_항목의_targetDate_id_기준으로_다음_페이지를_조회한다")
    void 커서는_초기값에서_시작해서_직전_항목의_targetDate_id_기준으로_다음_페이지를_조회한다() {
      // given
      WeatherD1BaselineRetentionItem item1 = new WeatherD1BaselineRetentionItem(
          UUID.randomUUID(), LocalDate.parse("2026-07-01"));
      WeatherD1BaselineRetentionItem item2 = new WeatherD1BaselineRetentionItem(
          UUID.randomUUID(), LocalDate.parse("2026-07-02"));
      WeatherD1BaselineRetentionItem item3 = new WeatherD1BaselineRetentionItem(
          UUID.randomUUID(), LocalDate.parse("2026-07-03"));
      given(weatherD1BaselineRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of(item1, item2), List.of(item3), List.of());

      // when
      reader.read();
      reader.read();
      reader.read();

      // then
      ArgumentCaptor<LocalDate> targetDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
      ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
      verify(weatherD1BaselineRepository, times(2))
          .findForRetention(any(), targetDateCaptor.capture(), idCaptor.capture(), anyInt());

      List<LocalDate> capturedTargetDates = targetDateCaptor.getAllValues();
      List<UUID> capturedIds = idCaptor.getAllValues();

      assertThat(capturedTargetDates.get(0)).isEqualTo(LocalDate.EPOCH);
      assertThat(capturedIds.get(0)).isEqualTo(new UUID(0L, 0L));

      assertThat(capturedTargetDates.get(1)).isEqualTo(item2.targetDate());
      assertThat(capturedIds.get(1)).isEqualTo(item2.id());
    }

    @Test
    @DisplayName("cutoff는_오늘_KST_날짜를_그대로_넘긴다")
    void cutoff는_오늘_KST_날짜를_그대로_넘긴다() {
      // given
      given(weatherD1BaselineRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of());
      LocalDate expectedCutoff = LocalDate.now(Clock.fixed(FIXED_NOW, ZoneId.of("UTC")).withZone(KST));

      // when
      reader.read();

      // then
      verify(weatherD1BaselineRepository)
          .findForRetention(eq(expectedCutoff), any(), any(), anyInt());
    }

    @Test
    @DisplayName("chunkSize를_limit으로_그대로_넘긴다")
    void chunkSize를_limit으로_그대로_넘긴다() {
      // given
      given(weatherD1BaselineRepository.findForRetention(any(), any(), any(), anyInt()))
          .willReturn(List.of());

      // when
      reader.read();

      // then
      verify(weatherD1BaselineRepository).findForRetention(any(), any(), any(), eq(2));
    }
  }
}