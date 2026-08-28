package com.sprint.mission.otboo.domain.social.feed.event;

import java.util.UUID;

/**
 * 즉시 반영이 필요하지 않은 피드 검색 인덱스 동기화 요청.
 *
 * <p>좋아요 증감처럼 색인이 늦어도 사용자가 알아채기 어려운 경로에서 쓴다.
 * {@code likedByMe}와 {@code likeCount}는 모두 DB에서 조회하므로 목록에서도 즉시 정확하고, 색인 지연은
 * {@code sortBy=likeCount} 정렬 순서에만 최대 1초 영향을 준다.
 *
 * <p>등록·수정·삭제는 {@link FeedIndexRequestedEvent}로 동기 처리한다. 목록 조회가 ES 검색이라
 * 색인이 늦으면 방금 등록한 피드가 목록에 나오지 않는다.
 */
public record FeedIndexAsyncRequestedEvent(UUID feedId) {

  public static FeedIndexAsyncRequestedEvent upsert(UUID feedId) {
    return new FeedIndexAsyncRequestedEvent(feedId);
  }
}
