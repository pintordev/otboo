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
import com.sprint.mission.otboo.batch.weatherfetch.metrics.WeatherFetchMetrics;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.LocalDate;
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

  private static final FixtureMonkey ENTITY_FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private WeatherRepository weatherRepository;
  @Mock
  private WeatherSuddenChangeChunkProcessor chunkProcessor;
  @Mock
  private WeatherFetchMetrics weatherFetchMetrics;

  private WeatherSuddenChangeNotifier notifier;

  private WeatherSuddenChangeNotifier notifierWithChunkSize(int gridChunkSize) {
    WeatherChangeProperties properties =
        new WeatherChangeProperties(1.0, 10.0, 1.0, gridChunkSize);
    return new WeatherSuddenChangeNotifier(weatherRepository, chunkProcessor, properties,
        weatherFetchMetrics);
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
    @DisplayName("청크별_결과를_합산해_D0_D1_알림_건수를_메트릭에_기록한다")
    void 청크별_결과를_합산해_D0_D1_알림_건수를_메트릭에_기록한다() {
      notifier = notifierWithChunkSize(1);
      BaseTime baseTime = new BaseTime("20260727", "0800");
      List<WeatherGrid> grids = List.of(gridWithId(60, 127), gridWithId(61, 128));
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(grids);
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(
              new WeatherSuddenChangeChunkProcessor.ChunkResult(1, 1),
              new WeatherSuddenChangeChunkProcessor.ChunkResult(2, 1));

      notifier.detectAndNotify(baseTime);

      verify(weatherFetchMetrics).countNotified("D0", 3);
      verify(weatherFetchMetrics).countNotified("D1", 2);
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

      verify(chunkProcessor).process(any(), eq(baseTime), eq(LocalDate.parse("2026-07-27")),
          anyBoolean(), eq(true));
    }

    @Test
    @DisplayName("지연_실행으로_자정을_넘겨도_D1_날짜는_시스템_시각이_아닌_baseTime_기준으로_계산한다")
    void 지연_실행으로_자정을_넘겨도_D1_날짜는_시스템_시각이_아닌_baseTime_기준으로_계산한다() {
      // baseTime의 baseDate가 "실제 실행 시각의 오늘 날짜"와 다를 수 있다(예: 20시 배치가
      // 지연되어 자정을 넘겨 실행됨) - today는 시스템 시각이 아니라 baseTime.baseDate()로
      // 계산해야 한다.
      BaseTime baseTime = new BaseTime("20260815", "2000");
      WeatherGrid grid = gridWithId(60, 127);
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(List.of(grid));
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(0, 0));

      notifier.detectAndNotify(baseTime);

      verify(chunkProcessor).process(any(), eq(baseTime), eq(LocalDate.parse("2026-08-15")),
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

    @Test
    @DisplayName("한_청크가_예외로_실패해도_나머지_청크는_계속_처리된다")
    void 한_청크가_예외로_실패해도_나머지_청크는_계속_처리된다() {
      notifier = notifierWithChunkSize(1);
      BaseTime baseTime = new BaseTime("20260727", "0800");
      List<WeatherGrid> grids = List.of(gridWithId(60, 127), gridWithId(61, 128),
          gridWithId(62, 129));
      given(weatherRepository.findGridsUpdatedAt(baseTime.toInstant())).willReturn(grids);
      given(chunkProcessor.process(any(), any(), any(), anyBoolean(), anyBoolean()))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(1, 0))
          .willThrow(new RuntimeException("청크 처리 실패"))
          .willReturn(new WeatherSuddenChangeChunkProcessor.ChunkResult(1, 0));

      notifier.detectAndNotify(baseTime);

      // then - 실패한 2번째 청크와 무관하게 3번째 청크까지 전부 호출돼야 한다
      verify(chunkProcessor, times(3)).process(any(), any(), any(), anyBoolean(), anyBoolean());
      verify(weatherFetchMetrics).countNotified("D0", 2); // 1번째+3번째 청크 합산, 2번째는 실패로 0
    }
  }
}