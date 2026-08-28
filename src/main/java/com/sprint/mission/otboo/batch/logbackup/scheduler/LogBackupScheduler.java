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

  // lockAtMostFor 기본값 PT1H — LogBackupReader.read()는 (로그그룹 × lookbackDays) 대상마다
  // nextToken이 없어질 때까지 FilterLogEvents를 무제한 페이지네이션하므로 실행 시간 상한이 없다.
  // 로그 그룹 3개 × lookbackDays 기본 3일 규모에서도 최악 실행 시간을 넉넉히 덮도록 여유를 둔다
  // (CodeRabbit 리뷰 반영).
  @SchedulerLock(
      name = "LogBackupSchedulerLock",
      lockAtMostFor = "${batch.log-backup.lock-at-most-for:PT1H}",
      lockAtLeastFor = "${batch.log-backup.lock-at-least-for:PT1M}")
  @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
  public void upload() {
    log.info("로그 백업 배치 시작");
    logBackupService.executeBackup();
    log.info("로그 백업 배치 완료");
  }
}
