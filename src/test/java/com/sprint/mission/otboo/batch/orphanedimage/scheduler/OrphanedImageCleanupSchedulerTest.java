package com.sprint.mission.otboo.batch.orphanedimage.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.mission.otboo.batch.orphanedimage.service.OrphanedImageCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrphanedImageCleanupSchedulerTest {

  @InjectMocks
  private OrphanedImageCleanupScheduler scheduler;

  @Mock
  private OrphanedImageCleanupService orphanedImageCleanupService;

  @Nested
  @DisplayName("CleanUp")
  class CleanUp {

    @Test
    @DisplayName("OrphanedImageCleanupService_execute만_호출하고_다른_로직은_없다")
    void OrphanedImageCleanupService_execute만_호출하고_다른_로직은_없다() {
      // when
      scheduler.cleanUp();

      // then
      verify(orphanedImageCleanupService).execute();
      verifyNoMoreInteractions(orphanedImageCleanupService);
    }
  }
}
