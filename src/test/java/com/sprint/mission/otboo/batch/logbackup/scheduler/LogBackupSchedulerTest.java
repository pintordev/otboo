package com.sprint.mission.otboo.batch.logbackup.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.mission.otboo.batch.logbackup.service.LogBackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogBackupSchedulerTest {

  @InjectMocks
  private LogBackupScheduler scheduler;

  @Mock
  private LogBackupService logBackupService;

  @Nested
  @DisplayName("업로드 스케줄")
  class Upload {

    @Test
    @DisplayName("호출되면_LogBackupService_executeBackup만_호출하고_다른_로직은_없다")
    void 호출되면_LogBackupService_executeBackup만_호출하고_다른_로직은_없다() {
      // when
      scheduler.upload();

      // then
      verify(logBackupService).executeBackup();
      verifyNoMoreInteractions(logBackupService);
    }
  }
}
