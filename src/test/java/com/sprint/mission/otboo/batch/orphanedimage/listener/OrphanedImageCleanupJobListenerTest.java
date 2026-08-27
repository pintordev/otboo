package com.sprint.mission.otboo.batch.orphanedimage.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;

@ExtendWith(MockitoExtension.class)
class OrphanedImageCleanupJobListenerTest {

  private OrphanedImageCleanupJobListener listener;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @Mock
  private JobExecution jobExecution;

  @Mock
  private OrphanedImageCleanupMetrics orphanedImageCleanupMetrics;

  @BeforeEach
  void setUp() {
    listener = new OrphanedImageCleanupJobListener(orphanedImageCleanupMetrics);
    logger = (Logger) LoggerFactory.getLogger(OrphanedImageCleanupJobListener.class);
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

      // when
      listener.beforeJob(jobExecution);

      // then
      assertThat(appender.list).anySatisfy(
          event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
    }
  }
}
