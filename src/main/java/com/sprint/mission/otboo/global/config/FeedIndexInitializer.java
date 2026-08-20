package com.sprint.mission.otboo.global.config;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 피드 검색 인덱스를 명시적으로 생성한다.
 *
 * <p>Spring Data Elasticsearch의 자동 생성(@Document(createIndex = true))에 의존하면
 * 인덱스 생성 주체가 코드에 드러나지 않고, 다중 인스턴스가 동시에 기동할 때 경합이 발생한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedIndexInitializer implements ApplicationRunner {

  private static final String ALREADY_EXISTS = "resource_already_exists_exception";

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public void run(ApplicationArguments args) {
    IndexOperations indexOps = elasticsearchOperations.indexOps(FeedDocument.class);
    if (indexOps.exists()) {
      return;
    }

    try {
      indexOps.createWithMapping();
      log.info("피드 검색 인덱스 생성 완료");
    } catch (DataAccessException e) {
      // 다중 인스턴스 동시 기동 시 다른 인스턴스가 먼저 생성한 경우만 흡수한다.
      // 연결 실패나 매핑 오류는 그대로 전파해 기동 실패로 드러나게 한다.
      if (!isAlreadyExists(e)) {
        throw e;
      }
      log.warn("피드 검색 인덱스가 이미 존재합니다.");
    }
  }

  private boolean isAlreadyExists(DataAccessException e) {
    String message = e.getMessage();
    return message != null && message.contains(ALREADY_EXISTS);
  }
}
