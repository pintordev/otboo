package com.sprint.mission.otboo.batch.feedmigration.endpoint;

import com.sprint.mission.otboo.batch.feedmigration.service.FeedIndexMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

/**
 * 피드 인덱스 매핑 마이그레이션 트리거.
 *
 * <p>매핑 변경은 배포와 함께 일어나므로 사람이 시점을 정한다. 스케줄러에 걸면 의도치 않은 시점에
 * 인덱스가 바뀌고, 기동 경로에 두면 다중 인스턴스가 각자 새 인덱스를 만들어 alias가 엉킨다.
 *
 * <p>{@code /actuator/**}는 ADMIN 권한이 걸려 있다(SecurityConfig).
 */
@Component
@RequiredArgsConstructor
@Endpoint(id = "feedindexmigration")
public class FeedIndexMigrationEndpoint {

  private final FeedIndexMigrationService feedIndexMigrationService;

  @WriteOperation
  public void migrate() {
    feedIndexMigrationService.migrate();
  }
}
