package com.sprint.mission.otboo.batch.weatherretention.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sprint.mission.otboo.batch.weatherretention.metrics.WeatherRetentionMetrics;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;

@ExtendWith(MockitoExtension.class)
class WeatherRetentionStepListenerTest {

  private WeatherRetentionStepListener listener;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @Mock
  private StepExecution stepExecution;

  @Mock
  private WeatherRetentionMetrics weatherRetentionMetrics;

  @BeforeEach
  void setUp() {
    listener = new WeatherRetentionStepListener(weatherRetentionMetrics);
    logger = (Logger) LoggerFactory.getLogger(WeatherRetentionStepListener.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Nested
  @DisplayName("AfterStep")
  class AfterStep {

    @Test
    @DisplayName("시작_종료_시각이_없으면_경고_로그만_남기고_ExitStatus를_그대로_반환한다")
    void 시작_종료_시각이_없으면_경고_로그만_남기고_ExitStatus를_그대로_반환한다() {
      // given
      given(stepExecution.getStartTime()).willReturn(null);
      given(stepExecution.getEndTime()).willReturn(null);
      given(stepExecution.getExitStatus()).willReturn(ExitStatus.COMPLETED);

      // when
      ExitStatus result = listener.afterStep(stepExecution);

      // then
      assertThat(result).isEqualTo(ExitStatus.COMPLETED);
      assertThat(appender.list).anySatisfy(
          event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    @DisplayName("시작_종료_시각이_있으면_처리건수와_소요시간을_로그로_남기고_ExitStatus를_그대로_반환한다")
    void 시작_종료_시각이_있으면_처리건수와_소요시간을_로그로_남기고_ExitStatus를_그대로_반환한다() {
      // given
      given(stepExecution.getStartTime()).willReturn(LocalDateTime.of(2026, 8, 7, 4, 0, 0));
      given(stepExecution.getEndTime()).willReturn(LocalDateTime.of(2026, 8, 7, 4, 0, 5));
      given(stepExecution.getReadCount()).willReturn(10L);
      given(stepExecution.getWriteCount()).willReturn(9L);
      given(stepExecution.getExitStatus()).willReturn(ExitStatus.COMPLETED);

      // when
      ExitStatus result = listener.afterStep(stepExecution);

      // then
      assertThat(result).isEqualTo(ExitStatus.COMPLETED);
      assertThat(appender.list).anySatisfy(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage()).contains("readCount=10", "writeCount=9");
      });
    }

    @Test
    @DisplayName("시작_종료_시각이_있으면_writeCount를_Step_이름_태그로_정리_건수_카운터에_기록한다")
    void 시작_종료_시각이_있으면_writeCount를_Step_이름_태그로_정리_건수_카운터에_기록한다() {
      // given
      given(stepExecution.getStartTime()).willReturn(LocalDateTime.of(2026, 8, 7, 4, 0, 0));
      given(stepExecution.getEndTime()).willReturn(LocalDateTime.of(2026, 8, 7, 4, 0, 5));
      given(stepExecution.getWriteCount()).willReturn(9L);
      given(stepExecution.getStepName()).willReturn("weatherRetentionStep");
      given(stepExecution.getExitStatus()).willReturn(ExitStatus.COMPLETED);

      // when
      listener.afterStep(stepExecution);

      // then - weatherRetentionStep과 weatherD1BaselineRetentionStep이 같은 리스너를 공유하므로
      // Step 이름 태그로 구분해야 정리 건수가 두 테이블 기준으로 섞이지 않는다
      verify(weatherRetentionMetrics).countCleaned("weatherRetentionStep", 9L);
    }

    @Test
    @DisplayName("ExitStatus가_FAILED면_error_로그를_남기고_ExitStatus를_그대로_반환한다")
    void ExitStatus가_FAILED면_error_로그를_남기고_ExitStatus를_그대로_반환한다() {
      // given
      given(stepExecution.getStartTime()).willReturn(LocalDateTime.of(2026, 8, 7, 4, 0, 0));
      given(stepExecution.getEndTime()).willReturn(LocalDateTime.of(2026, 8, 7, 4, 0, 5));
      given(stepExecution.getExitStatus()).willReturn(ExitStatus.FAILED);

      // when
      ExitStatus result = listener.afterStep(stepExecution);

      // then
      assertThat(result).isEqualTo(ExitStatus.FAILED);
      assertThat(appender.list).anySatisfy(
          event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }
  }
}