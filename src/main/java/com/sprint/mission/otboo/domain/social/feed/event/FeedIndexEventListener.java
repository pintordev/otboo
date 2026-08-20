package com.sprint.mission.otboo.domain.social.feed.event;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.event.FeedIndexRequestedEvent.IndexAction;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
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

  // 인덱싱 실패가 본 기능을 막지 않도록 로그만 남긴다. 복구는 정합성 배치가 담당한다.
  // 로그 수집에서 마커로 검색해 실패 건을 추출할 수 있도록 고정 형식으로 기록한다.
  private void logIndexFailure(FeedIndexRequestedEvent event, String reason, Exception e) {
    log.error("{} feedId={}, action={}, reason={}",
        INDEX_FAILURE_MARKER, event.feedId(), event.action(), reason, e);
  }
}
