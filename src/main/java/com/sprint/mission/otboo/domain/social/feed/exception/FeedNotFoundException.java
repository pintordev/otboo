package com.sprint.mission.otboo.domain.social.feed.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FeedNotFoundException extends FeedException {

  private static final String MESSAGE = "피드를 찾을 수 없습니다.";

  private FeedNotFoundException(Map<String, Object> details) {
    super(HttpStatus.NOT_FOUND, MESSAGE, details);
  }

  public static FeedNotFoundException withId(UUID feedId) {
    return new FeedNotFoundException(Map.of("feedId", feedId));
  }
}