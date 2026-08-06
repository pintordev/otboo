package com.sprint.mission.otboo.domain.social.feed.service;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedForbiddenException;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.mapper.FeedMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedLikeRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class FeedService {

  private static final String UQ_FEED_LIKES = "uq_feed_likes_feed_id_user_id";

  private final FeedRepository feedRepository;
  private final UserSummaryQueryRepository userSummaryQueryRepository;
  private final FeedMapper feedMapper;
  private final WeatherSnapshotProvider weatherSnapshotProvider;
  private final OotdSnapshotProvider ootdSnapshotProvider;
  private final FeedLikeRepository feedLikeRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public FeedDto create(FeedCreateRequest request, UUID currentUserId) {
    if (!request.authorId().equals(currentUserId)) {
      throw FeedForbiddenException.authorMismatch(currentUserId, request.authorId());
    }

    WeatherSnapshot weatherSnapshot = weatherSnapshotProvider.readSnapshot(request.weatherId());
    List<OotdSnapshot> ootdSnapshots = ootdSnapshotProvider.readOotds(request.clothesIds(),
        request.authorId());

    Feed feed = feedRepository.save(
        Feed.create(request.authorId(), request.weatherId(), request.content(), weatherSnapshot,
            ootdSnapshots));
    log.info("피드 등록 완료: feedId={}", feed.getId());

    UserSummary author = userSummaryQueryRepository.findByUserId(feed.getAuthorId());
    return feedMapper.toDto(feed, author, false);
  }

  public CursorPageResponse<FeedDto> getFeeds(FeedListParams params, UUID currentUserId) {
    CursorPageResponse<Feed> page = feedRepository.findFeeds(params);

    List<Feed> feeds = page.data();

    List<UUID> authorIds = feeds.stream()
        .map(Feed::getAuthorId)
        .distinct()
        .toList();

    Map<UUID, UserSummary> authorMap = userSummaryQueryRepository.findByUserIds(authorIds).stream()
        .collect(Collectors.toMap(UserSummary::userId, Function.identity(),
            (existing, replacement) -> existing));

    List<UUID> feedIds = feeds.stream()
        .map(Feed::getId)
        .toList();

    Set<UUID> likedFeedIds = feedIds.isEmpty()
        ? Set.of()
        : new HashSet<>(feedLikeRepository.findLikedFeedIds(currentUserId, feedIds));

    List<FeedDto> data = feeds.stream()
        .map(feed -> {
          UserSummary author = authorMap.get(feed.getAuthorId());
          boolean likedByMe = likedFeedIds.contains(feed.getId());
          return feedMapper.toDto(feed, author, likedByMe);
        })
        .toList();

    return new CursorPageResponse<>(
        data, page.nextCursor(), page.nextIdAfter(), page.hasNext(),
        page.totalCount(), page.sortBy(), page.sortDirection());
  }

  @Transactional
  public void like(UUID feedId, UUID currentUserId) {
    if (!feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)) {
      throw FeedNotFoundException.withId(feedId);
    }
    if (feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId)) {
      return;
    }
    try {
      feedLikeRepository.save(FeedLike.create(feedId, currentUserId));
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) {
        log.warn("피드 좋아요 중 동시성 충돌 발생: feedId={}", feedId);
        return;
      }
      throw e;
    }
    feedRepository.incrementLikeCount(feedId);
    log.info("피드 좋아요 완료: feedId={}", feedId);

    UUID authorId = feedRepository.findAuthorId(feedId)
        .orElseThrow(() -> FeedNotFoundException.withId(feedId));
    UserSummary liker = userSummaryQueryRepository.findByUserId(currentUserId);
    eventPublisher.publishEvent(new NotificationRequestedEvent(
        Set.of(authorId), "좋아요",
        liker.name() + "님이 내 피드를 좋아합니다.", NotificationLevel.INFO));
  }

  @Transactional
  public void unlike(UUID feedId, UUID currentUserId) {
    if (!feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)) {
      throw FeedNotFoundException.withId(feedId);
    }
    if (feedLikeRepository.deleteByFeedIdAndUserId(feedId, currentUserId) > 0) {
      feedRepository.decrementLikeCount(feedId);
      log.info("피드 좋아요 취소 완료: feedId={}", feedId);
    }
  }


  private boolean isUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException cve
        && UQ_FEED_LIKES.equalsIgnoreCase(cve.getConstraintName());
  }
}