package com.sprint.mission.otboo.batch.weatherfetch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.batch.weatherfetch.config.WeatherChangeProperties;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherSuddenChangeNotifier")
class WeatherSuddenChangeNotifierTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final Clock CLOCK = Clock.fixed(
      java.time.Instant.parse("2026-07-27T01:00:00Z"), KST);

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private WeatherSuddenChangeChunkProcessor chunkProcessor;

  private WeatherSuddenChangeNotifier notifier;

  private WeatherSuddenChangeNotifier notifierWithChunkSize(int gridChunkSize) {
    WeatherChangeProperties properties =
        new WeatherChangeProperties(1.0, 10.0, 1.0, gridChunkSize);
    return new WeatherSuddenChangeNotifier(weatherRepository, chunkProcessor, properties, CLOCK);
  }

  @BeforeEach
  void setUp() {
    notifier = notifierWithChunkSize(500);
  }

  private WeatherGrid gridWithId(int x, int y) {
    return ENTITY_FIXTURE_MONKEY.giveMeBuilder(WeatherGrid.class)
        .set("x", x)
        .set("y", y)
        .sample();
  }

  @Nested
  @DisplayName("DetectAndNotify")
  class DetectAndNotify {

    @Test
    @DisplayName("격자_업데이트가_없으면_경고_로그만_남기고_청크_처리를_하지_않는다")
    void 격자_업데이트가_없으면_경고_로그만_남기고_청크_처리를_하지_않는다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of());

      notifier.detectAndNotify(baseTime);

      verify(chunkProcessor, never()).process(any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("gridChunkSize_단위로_나눠_청크마다_process를_호출한다")
    void gridChunkSize_단위로_나눠_청크마다_process를_호출한다() {
      notifier = notifierWithChunkSize(2);
      BaseTime baseTime = new BaseTime("20260727", "0800");
      List<WeatherGrid> grids = List.of(gridWithId(60, 127), gridWithId(61, 128),
          gridWithId(62, 129), gridWithId(63, 130), gridWithId(64, 131));
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(grids);
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(0, 0));

      notifier.detectAndNotify(baseTime);

      ArgumentCaptor<List<WeatherGrid>> captor = ArgumentCaptor.forClass(List.class);
      verify(chunkProcessor, times(3)).process(captor.capture(), eq(baseTime), any(),
          anyBoolean(), anyBoolean());
      assertThat(captor.getAllValues())
          .extracting(List::size)
          .containsExactly(2, 2, 1);
    }

    @Test
    @DisplayName("baseTime이_2300이면_shouldHandleD0를_false로_전달한다")
    void baseTime이_2300이면_shouldHandleD0를_false로_전달한다() {
      BaseTime baseTime = new BaseTime("20260727", "2300");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(0, 0));

      notifier.detectAndNotify(baseTime);

      verify(chunkProcessor).process(any(), eq(baseTime), any(), eq(false), anyBoolean());
    }

    @Test
    @DisplayName("baseTime이_2300이_아니면_shouldHandleD0를_true로_전달한다")
    void baseTime이_2300이_아니면_shouldHandleD0를_true로_전달한다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(0, 0));

      notifier.detectAndNotify(baseTime);

      verify(chunkProcessor).process(any(), eq(baseTime), any(), eq(true), anyBoolean());
    }

    @Test
    @DisplayName("baseTime이_2000이면_shouldHandleD1을_true로_전달한다")
    void baseTime이_2000이면_shouldHandleD1을_true로_전달한다() {
      BaseTime baseTime = new BaseTime("20260727", "2000");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(0, 0));

      notifier.detectAndNotify(baseTime);

      // CLOCK 고정 시각(2026-07-27 KST) 기준 today = 2026-07-27
      verify(chunkProcessor).process(any(), eq(baseTime), eq(LocalDate.parse("2026-07-27")),
          anyBoolean(), eq(true));
    }

    @Test
    @DisplayName("baseTime이_2000이_아니면_shouldHandleD1을_false로_전달한다")
    void baseTime이_2000이_아니면_shouldHandleD1을_false로_전달한다() {
      BaseTime baseTime = new BaseTime("20260727", "0800");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(0, 0));

      notifier.detectAndNotify(baseTime);

      verify(chunkProcessor).process(any(), eq(baseTime), any(), anyBoolean(), eq(false));
    }
  }
}