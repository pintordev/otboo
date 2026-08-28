package com.sprint.mission.otboo.batch.feedreindex.endpoint;

import com.sprint.mission.otboo.batch.feedreindex.dto.FeedReindexResult;
import com.sprint.mission.otboo.batch.feedreindex.service.FeedReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

/**
 * 피드 전체 재색인 트리거.
 *
 * <p>정합성 재색인은 주 1회 스케줄로 돌지만, 인덱스를 새로 만든 직후처럼 즉시 채워야 하는
 * 상황이 있다. 색인 실패가 쌓였을 때도 쓴다.
 *
 * <p>{@code /actuator/**}는 ADMIN 권한이 걸려 있다(SecurityConfig).
 */
@Component
@RequiredArgsConstructor
@Endpoint(id = "feedreindex")
public class FeedReindexEndpoint {

  private final FeedReindexService feedReindexService;

  @WriteOperation
  public FeedReindexResult reindexAll() {
    return feedReindexService.executeReindexAll();
  }
}
