package com.sprint.mission.otboo.domain.social.feed.event;

import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import com.sprint.mission.otboo.domain.social.feed.event.FeedIndexRequestedEvent.IndexAction;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.elasticsearch.FeedSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedIndexEventListener {

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
          .map(FeedDocument::from)
          .ifPresent(document -> {
            feedSearchRepository.save(document);
            log.debug("피드 검색 인덱싱 완료: feedId={}", event.feedId());
          });
    } catch (Exception e) {
      log.error("피드 검색 인덱싱 실패: feedId={}, action={}",
          event.feedId(), event.action(), e);
    }
  }
}
