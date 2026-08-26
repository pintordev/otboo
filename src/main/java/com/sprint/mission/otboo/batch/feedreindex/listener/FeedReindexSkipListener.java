package com.sprint.mission.otboo.batch.feedreindex.listener;

import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FeedReindexSkipListener implements SkipListener<Feed, Feed> {

  private static final String SKIP_MARKER = "FEED_REINDEX_SKIPPED";

  @Override
  public void onSkipInWrite(Feed item, Throwable t) {
    log.error("{} feedId={}", SKIP_MARKER, item.getId(), t);
  }
}
