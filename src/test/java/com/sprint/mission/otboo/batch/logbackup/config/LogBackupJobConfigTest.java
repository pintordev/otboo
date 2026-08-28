package com.sprint.mission.otboo.batch.logbackup.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties.LogGroupTarget;
import com.sprint.mission.otboo.batch.logbackup.listener.LogBackupJobListener;
import com.sprint.mission.otboo.batch.logbackup.listener.LogBackupStepListener;
import com.sprint.mission.otboo.batch.logbackup.processor.LogBackupProcessor;
import com.sprint.mission.otboo.batch.logbackup.reader.LogBackupReader;
import com.sprint.mission.otboo.batch.logbackup.writer.LogBackupWriter;
import com.sprint.mission.otboo.global.batch.SkipLoggingListener;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class LogBackupJobConfigTest {

  private final JobRepository jobRepository = mock(JobRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);

  @Nested
  @DisplayName("LogBackupJobConfig Job, Step 테스트")
  class JobStepTest {

    @Test
    @DisplayName("Job_Step_생성_성공")
    void Job_Step_생성_성공() {
      // given
      LogBackupJobListener jobListener = mock(LogBackupJobListener.class);
      LogBackupStepListener stepListener = mock(LogBackupStepListener.class);
      SkipLoggingListener skipLoggingListener = mock(SkipLoggingListener.class);
      LogBackupReader reader = mock(LogBackupReader.class);
      LogBackupProcessor processor = mock(LogBackupProcessor.class);
      LogBackupWriter writer = mock(LogBackupWriter.class);
      LogBackupProperties properties = new LogBackupProperties(
          List.of(new LogGroupTarget("/ecs/otboo", "app"),
              new LogGroupTarget("/ecs/otboo-nginx", "nginx"),
              new LogGroupTarget("/ecs/otboo-elasticsearch", "es")),
          "otboo-backup", 10, 5, 3, 3);

      LogBackupJobConfig config = new LogBackupJobConfig(jobRepository, transactionManager,
          jobListener, stepListener, skipLoggingListener, reader, processor, writer, properties);

      // when
      Job job = config.logBackupJob();
      Step step = config.logBackupStep();

      // then
      assertNotNull(job);
      assertNotNull(step);
      assertThat(job.getName()).isEqualTo("logBackupJob");
      assertThat(step.getName()).isEqualTo("logBackupStep");
    }
  }
}
