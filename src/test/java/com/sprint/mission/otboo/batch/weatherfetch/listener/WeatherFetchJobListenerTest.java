package com.sprint.mission.otboo.batch.weatherfetch.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sprint.mission.otboo.batch.weatherfetch.service.WeatherSuddenChangeNotifier;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.infrastructure.item.ExecutionContext;

@ExtendWith(MockitoExtension.class)
class WeatherFetchJobListenerTest {

  private WeatherFetchJobListener listener;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @Mock
  private JobExecution jobExecution;

  @Mock
  private Clock clock;

  @Mock
  private WeatherSuddenChangeNotifier weatherSuddenChangeNotifier;

  @BeforeEach
  void setUp() {
    listener = new WeatherFetchJobListener(clock, weatherSuddenChangeNotifier);
    logger = (Logger) LoggerFactory.getLogger(WeatherFetchJobListener.class);
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
  @DisplayName("BeforeJob")
  class BeforeJob {

    @Test
    @DisplayName("Job_시작_정보를_info_로그로_남긴다")
    void Job_시작_정보를_info_로그로_남긴다() {
      // given
      given(jobExecution.getId()).willReturn(1L);
      given(jobExecution.getJobParameters()).willReturn(new JobParameters());
      given(clock.instant()).willReturn(Instant.parse("2026-07-27T09:00:00Z"));
      given(jobExecution.getExecutionContext()).willReturn(new ExecutionContext());

      // when
      listener.beforeJob(jobExecution);

      // then
      assertThat(appender.list).isNotEmpty();
      assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("baseDate_baseTime을_한_번_계산해_JobExecutionContext에_저장한다")
    void baseDate_baseTime을_한_번_계산해_JobExecutionContext에_저장한다() {
      // given - 2026-07-27 18:00 KST 고정, 17시 발표가 최신
      given(jobExecution.getId()).willReturn(1L);
      given(jobExecution.getJobParameters()).willReturn(new JobParameters());
      given(clock.instant()).willReturn(Instant.parse("2026-07-27T09:00:00Z"));
      ExecutionContext executionContext = new ExecutionContext();
      given(jobExecution.getExecutionContext()).willReturn(executionContext);

      // when
      listener.beforeJob(jobExecution);

      // then - Reader/Processor가 각자 Clock으로 계산하는 대신, Job 시작 시 한 번 계산된 이
      // 값을 JobExecutionContext를 통해 공유받아 Step 경계를 넘어도 동일한 baseTime을 쓴다
      assertThat(executionContext.getString("baseDate")).isEqualTo("20260727");
      assertThat(executionContext.getString("baseTime")).isEqualTo("1700");
    }
  }

  @Nested
  @DisplayName("AfterJob")
  class AfterJob {

    @Test
    @DisplayName("COMPLETED면_성공_로그를_남긴다")
    void COMPLETED면_성공_로그를_남긴다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      given(jobExecution.getAllFailureExceptions()).willReturn(List.of());

      // when
      listener.afterJob(jobExecution);

      // then
      assertThat(appender.list)
          .anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("성공");
          });
    }

    @Test
    @DisplayName("FAILED면_실패_로그를_남긴다")
    void FAILED면_실패_로그를_남긴다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.FAILED);
      given(jobExecution.getAllFailureExceptions()).willReturn(List.of());

      // when
      listener.afterJob(jobExecution);

      // then
      assertThat(appender.list)
          .anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("실패");
          });
    }

    @Test
    @DisplayName("시작_종료_시각이_모두_있으면_소요시간을_로그로_남긴다")
    void 시작_종료_시각이_모두_있으면_소요시간을_로그로_남긴다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      given(jobExecution.getStartTime()).willReturn(LocalDateTime.of(2026, 7, 27, 10, 0, 0));
      given(jobExecution.getEndTime()).willReturn(LocalDateTime.of(2026, 7, 27, 10, 0, 5));
      given(jobExecution.getAllFailureExceptions()).willReturn(List.of());

      // when
      listener.afterJob(jobExecution);

      // then
      assertThat(appender.list)
          .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("duration"));
    }

    @Test
    @DisplayName("실패_원인_예외가_있으면_각각_error_로그로_남긴다")
    void 실패_원인_예외가_있으면_각각_error_로그로_남긴다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.FAILED);
      given(jobExecution.getAllFailureExceptions())
          .willReturn(List.of(new RuntimeException("격자 조회 실패")));

      // when
      listener.afterJob(jobExecution);

      // then
      assertThat(appender.list)
          .filteredOn(event -> event.getThrowableProxy() != null)
          .anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("격자 조회 실패");
          });
    }

    @Test
    @DisplayName("COMPLETED면_ExecutionContext의_BaseTime으로_detectAndNotify를_호출한다")
    void COMPLETED면_ExecutionContext의_BaseTime으로_detectAndNotify를_호출한다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      given(jobExecution.getAllFailureExceptions()).willReturn(List.of());
      ExecutionContext executionContext = new ExecutionContext();
      executionContext.putString("baseDate", "20260727");
      executionContext.putString("baseTime", "0800");
      given(jobExecution.getExecutionContext()).willReturn(executionContext);

      // when
      listener.afterJob(jobExecution);

      // then
      verify(weatherSuddenChangeNotifier).detectAndNotify(new BaseTime("20260727", "0800"));
    }

    @Test
    @DisplayName("FAILED여도_detectAndNotify를_호출한다")
    void FAILED여도_detectAndNotify를_호출한다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.FAILED);
      given(jobExecution.getAllFailureExceptions()).willReturn(List.of());
      ExecutionContext executionContext = new ExecutionContext();
      executionContext.putString("baseDate", "20260727");
      executionContext.putString("baseTime", "0800");
      given(jobExecution.getExecutionContext()).willReturn(executionContext);

      // when
      listener.afterJob(jobExecution);

      // then - 실패 전에 성공 처리된 격자의 감지·알림은 Job 전체 성패와 무관하게 유효하다
      verify(weatherSuddenChangeNotifier).detectAndNotify(new BaseTime("20260727", "0800"));
    }

    @Test
    @DisplayName("detectAndNotify가_예외를_던져도_afterJob은_예외_없이_끝난다")
    void detectAndNotify가_예외를_던져도_afterJob은_예외_없이_끝난다() {
      // given
      given(jobExecution.getStatus()).willReturn(BatchStatus.COMPLETED);
      given(jobExecution.getAllFailureExceptions()).willReturn(List.of());
      ExecutionContext executionContext = new ExecutionContext();
      executionContext.putString("baseDate", "20260727");
      executionContext.putString("baseTime", "0800");
      given(jobExecution.getExecutionContext()).willReturn(executionContext);
      willThrow(new RuntimeException("감지 실패"))
          .given(weatherSuddenChangeNotifier).detectAndNotify(any());

      // when & then - detectAndNotify()의 예외가 Job 최종 상태를 되돌리면 안 된다
      assertThatCode(() -> listener.afterJob(jobExecution)).doesNotThrowAnyException();
      // 빈 catch 금지 - 흡수한 예외는 반드시 error 로그로 남겨야 한다(conventions.md 14번)
      assertThat(appender.list)
          .anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("날씨 급변 감지 실패");
          });
    }
  }
}