package com.sprint.mission.otboo.batch.logbackup.listener;

import com.sprint.mission.otboo.batch.logbackup.metrics.LogBackupMetrics;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LogBackupStepListener implements StepExecutionListener {

  private final LogBackupMetrics logBackupMetrics;

  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    if (stepExecution.getStartTime() == null || stepExecution.getEndTime() == null) {
      log.warn("LogBackup Step 시간 정보 누락 | startTime={}, endTime={}",
          stepExecution.getStartTime(), stepExecution.getEndTime());
      return stepExecution.getExitStatus();
    }

    Duration duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime());
    ExitStatus exitStatus = stepExecution.getExitStatus();

    logBackupMetrics.countStepSkipped(stepExecution.getSkipCount());

    if (ExitStatus.FAILED.getExitCode().equals(exitStatus.getExitCode())) {
      log.error(
          "LogBackup Step 실패 | readCount={}, writeCount={}, skipCount={}, duration={}, exitStatus={}",
          stepExecution.getReadCount(), stepExecution.getWriteCount(),
          stepExecution.getSkipCount(), duration, exitStatus);
    } else {
      log.info("LogBackup Step 완료 | readCount={}, writeCount={}, skipCount={}, duration={}",
          stepExecution.getReadCount(), stepExecution.getWriteCount(),
          stepExecution.getSkipCount(), duration);
    }

    return exitStatus;
  }
}
