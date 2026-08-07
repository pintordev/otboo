package com.sprint.mission.otboo.batch.weatherfetch.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

  @BeforeEach
  void setUp() {
    listener = new WeatherFetchJobListener(clock);
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
  }
}