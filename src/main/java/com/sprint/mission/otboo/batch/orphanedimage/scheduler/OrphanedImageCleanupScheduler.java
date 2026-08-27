package com.sprint.mission.otboo.batch.orphanedimage.scheduler;

import com.sprint.mission.otboo.batch.orphanedimage.service.OrphanedImageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanedImageCleanupScheduler {

  private final OrphanedImageCleanupService orphanedImageCleanupService;

  @SchedulerLock(
      name = "OrphanedImageCleanupSchedulerLock",
      lockAtMostFor = "${batch.orphaned-image-cleanup.lock-at-most-for:PT30M}",
      lockAtLeastFor = "${batch.orphaned-image-cleanup.lock-at-least-for:PT1M}")
  @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
  public void cleanUp() {
    log.info("유실 이미지 정리 배치 시작");
    orphanedImageCleanupService.execute();
    log.info("유실 이미지 정리 배치 완료");
  }
}
