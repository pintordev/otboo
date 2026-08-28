package com.sprint.mission.otboo.domain.social.feed.event;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.event.FeedIndexRequestedEvent.IndexAction;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedIndexEventListener {

  private static final String INDEX_FAILURE_MARKER = "FEED_INDEX_FAILED";

  private final FeedRepository feedRepository;
  private final FeedSearchRepository feedSearchRepository;
  private final ElasticsearchOperations elasticsearchOperations;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(FeedIndexRequestedEvent event) {
    try {
      if (event.action() == IndexAction.DELETE) {
        feedSearchRepository.deleteById(event.feedId().toString());
        log.debug("피드 검색 인덱스 제거 완료: feedId={}", event.feedId());
        return;
      }

      feedRepository.findById(event.feedId())
          .filter(feed -> !feed.isDeleted())
          .ifPresentOrElse(
              feed -> {
                feedSearchRepository.save(FeedDocument.from(feed));
                log.debug("피드 검색 인덱싱 완료: feedId={}", event.feedId());
              },
              // 삭제됐거나 존재하지 않으면 인덱스에서도 제거한다.
              // 이벤트 처리 순서가 뒤집혀도 삭제된 피드가 되살아나지 않는다.
              () -> feedSearchRepository.deleteById(event.feedId().toString()));
    } catch (DataAccessResourceFailureException e) {
      // ES 연결 자체가 안 되는 상황 — 이 시점의 모든 인덱싱이 실패 중이다.
      logIndexFailure(event, "CONNECTION", e);
    } catch (DataAccessException e) {
      // 문서 매핑 오류 등 — 해당 건만 실패한다.
      logIndexFailure(event, "DOCUMENT", e);
    } catch (Exception e) {
      logIndexFailure(event, "UNKNOWN", e);
    }
  }

  /**
   * 좋아요 증감으로 바뀐 {@code likeCount}를 인덱스에 반영한다.
   *
   * <p>요청 스레드에서 분리해 ES 응답을 기다리지 않는다.
   *
   * <p>대신 검색 반영이 응답보다 늦어진다. {@code likedByMe}와 {@code likeCount}는 DB에서
   * 조회하므로 값 자체는 즉시 정확하고, {@code sortBy=likeCount} 정렬 순서만 최대 1초 뒤에 맞춰진다(ES 기본 refresh 주기).
   */
  @Async("feedIndexExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleAsync(FeedIndexAsyncRequestedEvent event) {
    try {
      feedRepository.findById(event.feedId())
          .filter(feed -> !feed.isDeleted())
          .ifPresentOrElse(
              feed -> {
                elasticsearchOperations.save(FeedDocument.from(feed));
                log.debug("피드 검색 인덱싱 완료(비동기): feedId={}", event.feedId());
              },
              () -> elasticsearchOperations.delete(event.feedId().toString(), FeedDocument.class));
    } catch (DataAccessResourceFailureException e) {
      logAsyncIndexFailure(event.feedId(), "CONNECTION", e);
    } catch (DataAccessException e) {
      logAsyncIndexFailure(event.feedId(), "DOCUMENT", e);
    } catch (Exception e) {
      logAsyncIndexFailure(event.feedId(), "UNKNOWN", e);
    }
  }

  private void logAsyncIndexFailure(UUID feedId, String reason, Exception e) {
    log.error("{} feedId={}, action=UPSERT, reason={}", INDEX_FAILURE_MARKER, feedId, reason, e);
  }

  // 인덱싱 실패가 본 기능을 막지 않도록 로그만 남긴다. 복구는 정합성 배치가 담당한다.
  // 로그 수집에서 마커로 검색해 실패 건을 추출할 수 있도록 고정 형식으로 기록한다.
  private void logIndexFailure(FeedIndexRequestedEvent event, String reason, Exception e) {
    log.error("{} feedId={}, action={}, reason={}",
        INDEX_FAILURE_MARKER, event.feedId(), event.action(), reason, e);
  }
}
