package com.sprint.mission.otboo.domain.social.feed.event;

import java.util.UUID;

public record FeedIndexRequestedEvent(UUID feedId, IndexAction action) {

  public static FeedIndexRequestedEvent upsert(UUID feedId) {
    return new FeedIndexRequestedEvent(feedId, IndexAction.UPSERT);
  }

  public static FeedIndexRequestedEvent delete(UUID feedId) {
    return new FeedIndexRequestedEvent(feedId, IndexAction.DELETE);
  }

  public enum IndexAction {
    UPSERT, DELETE
  }
}
