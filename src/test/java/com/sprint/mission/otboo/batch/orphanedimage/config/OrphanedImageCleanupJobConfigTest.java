package com.sprint.mission.otboo.batch.orphanedimage.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.batch.orphanedimage.listener.OrphanedImageCleanupJobListener;
import com.sprint.mission.otboo.batch.orphanedimage.reader.OrphanedImageReader;
import com.sprint.mission.otboo.batch.orphanedimage.writer.OrphanedImageWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.transaction.PlatformTransactionManager;

class OrphanedImageCleanupJobConfigTest {

  private final JobRepository jobRepository = mock(JobRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);

  @Nested
  @DisplayName("OrphanedImageCleanupJobConfig Job, Step 테스트")
  class JobStepTest {

    @Test
    @DisplayName("Job_Step_생성_성공")
    void Job_Step_생성_성공() {
      // given
      OrphanedImageCleanupJobListener jobListener = mock(OrphanedImageCleanupJobListener.class);
      OrphanedImageReader reader = mock(OrphanedImageReader.class);
      OrphanedImageWriter writer = mock(OrphanedImageWriter.class);
      OrphanedImageCleanupProperties properties = new OrphanedImageCleanupProperties(
          List.of("profile/", "clothes/"), 24, 100, 0.3, 500);

      OrphanedImageCleanupJobConfig config = new OrphanedImageCleanupJobConfig(jobRepository,
          transactionManager, jobListener, reader, writer, properties);

      // when
      Job job = config.orphanedImageCleanupJob();
      Step step = config.orphanedImageCleanupStep();

      // then
      assertNotNull(job);
      assertNotNull(step);
      assertThat(job.getName()).isEqualTo("orphanedImageCleanupJob");
      assertThat(step.getName()).isEqualTo("orphanedImageCleanupStep");
    }
  }
}
