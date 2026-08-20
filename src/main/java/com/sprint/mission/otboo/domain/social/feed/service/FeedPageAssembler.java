package com.sprint.mission.otboo.domain.social.feed.service;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSearchResult;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.exception.AuthorNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.mapper.FeedMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedLikeRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elasticsearch 검색 결과를 DB 조회와 결합해 응답 페이지로 조립한다.
 *
 * <p>ES 호출은 외부 IO이므로 트랜잭션 밖에서 수행해야 하는데, 같은 클래스 안에서 메서드를 나누면
 * 프록시를 거치지 않아 {@code @Transactional}이 적용되지 않는다. 그래서 별도 컴포넌트로 분리했다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedPageAssembler {

  private final FeedRepository feedRepository;
  private final UserSummaryQueryRepository userSummaryQueryRepository;
  private final FeedLikeRepository feedLikeRepository;
  private final FeedMapper feedMapper;

  public CursorPageResponse<FeedDto> assemble(
      FeedSearchResult searchResult, FeedListParams params, UUID currentUserId) {
    List<Feed> feeds = findFeedsInSearchOrder(searchResult.feedIds());

    return toDtoPage(new CursorPageResponse<>(
        feeds,
        searchResult.nextCursor(),
        searchResult.nextIdAfter(),
        searchResult.hasNext(),
        searchResult.totalCount(),
        params.sortBy().name(),
        params.sortDirection()), currentUserId);
  }

  // findAllActiveByIds는 순서를 보장하지 않으므로 ES가 정렬한 순서로 되돌린다.
  private List<Feed> findFeedsInSearchOrder(List<UUID> feedIds) {
    if (feedIds.isEmpty()) {
      return List.of();
    }
    Map<UUID, Feed> feedMap = feedRepository.findAllActiveByIds(feedIds).stream()
        .collect(Collectors.toMap(Feed::getId, Function.identity()));
    return feedIds.stream()
        .map(feedMap::get)
        .filter(Objects::nonNull)
        .toList();
  }

  private CursorPageResponse<FeedDto> toDtoPage(CursorPageResponse<Feed> page, UUID currentUserId) {
    List<Feed> feeds = page.data();

    Map<UUID, UserSummary> authorMap = feeds.isEmpty() ? Map.of() :
        userSummaryQueryRepository.findByUserIds(
                feeds.stream().map(Feed::getAuthorId).distinct().toList()
            ).stream()
            .collect(Collectors.toMap(UserSummary::userId, Function.identity(),
                (existing, replacement) -> existing));

    Set<UUID> likedFeedIds = findLikedFeedIds(feeds, currentUserId);

    List<FeedDto> data = feeds.stream()
        .map(feed -> {
          UserSummary author = authorMap.get(feed.getAuthorId());
          if (author == null) {
            log.warn("피드 작성자 정보를 조회할 수 없습니다: feedId={}", feed.getId());
            throw AuthorNotFoundException.withNone();
          }
          return feedMapper.toDto(feed, author, likedFeedIds.contains(feed.getId()));
        })
        .toList();

    return new CursorPageResponse<>(data, page.nextCursor(), page.nextIdAfter(),
        page.hasNext(), page.totalCount(), page.sortBy(), page.sortDirection());
  }

  private Set<UUID> findLikedFeedIds(List<Feed> feeds, UUID currentUserId) {
    if (feeds.isEmpty()) {
      return Set.of();
    }
    List<UUID> feedIds = feeds.stream().map(Feed::getId).toList();
    return new HashSet<>(feedLikeRepository.findLikedFeedIds(currentUserId, feedIds));
  }
}
