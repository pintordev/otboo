package com.sprint.mission.otboo.batch.feedreindex.reader;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@RequiredArgsConstructor
@Component
public class FeedReindexReader implements ItemReader<Feed> {

  private static final Instant INITIAL_CREATED_AT = Instant.EPOCH;
  private static final UUID INITIAL_ID = new UUID(0L, 0L);

  private final FeedRepository feedRepository;
  private final FeedReindexProperties properties;

  private Instant lastCreatedAt = INITIAL_CREATED_AT;
  private UUID lastId = INITIAL_ID;
  private Iterator<Feed> iterator;

  @Override
  public Feed read() {
    while (iterator == null || !iterator.hasNext()) {
      List<Feed> feeds = feedRepository.findForReindex(
          lastCreatedAt, lastId, properties.chunkSize());
      if (feeds.isEmpty()) {
        return null;
      }
      iterator = feeds.iterator();
      log.info("피드 재색인 페이지 로드 완료: size={}", feeds.size());
    }

    Feed feed = iterator.next();
    lastCreatedAt = feed.getCreatedAt();
    lastId = feed.getId();
    return feed;
  }
}
