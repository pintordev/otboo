package com.sprint.mission.otboo.batch.logbackup.scheduler;

import com.sprint.mission.otboo.batch.logbackup.service.LogBackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogBackupScheduler {

  private final LogBackupService logBackupService;

  @SchedulerLock(
      name = "LogBackupSchedulerLock",
      lockAtMostFor = "${batch.log-backup.lock-at-most-for:PT30M}",
      lockAtLeastFor = "${batch.log-backup.lock-at-least-for:PT1M}")
  @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
  public void upload() {
    log.info("로그 백업 배치 시작");
    logBackupService.executeBackup();
    log.info("로그 백업 배치 완료");
  }
}
