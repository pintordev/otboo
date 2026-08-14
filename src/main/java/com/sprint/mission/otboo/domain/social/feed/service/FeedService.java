package com.sprint.mission.otboo.domain.social.feed.service;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedUpdateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import com.sprint.mission.otboo.domain.social.feed.event.FeedIndexRequestedEvent;
import com.sprint.mission.otboo.domain.social.feed.exception.AuthorNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedForbiddenException;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.mapper.FeedMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedLikeRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.follow.repository.FollowRepository;
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
  private final FollowRepository followRepository;

  @Transactional
  public FeedDto create(FeedCreateRequest request, UUID currentUserId) {
    validateAuthorMatchesCurrentUser(request.authorId(), currentUserId);

    Feed feed = feedRepository.save(createFeedWithSnapshots(request, currentUserId));
    log.info("피드 등록 완료: feedId={}", feed.getId());

    UserSummary author = userSummaryQueryRepository.findByUserId(feed.getAuthorId());
    publishFeedCreatedNotification(feed.getAuthorId(), author.name(), feed.getContent());
    eventPublisher.publishEvent(FeedIndexRequestedEvent.upsert(feed.getId()));

    return feedMapper.toDto(feed, author, false);
  }


  public CursorPageResponse<FeedDto> getFeeds(FeedListParams params, UUID currentUserId) {
    return toDtoPage(feedRepository.findFeeds(params), currentUserId);
  }

  @Transactional
  public void like(UUID feedId, UUID currentUserId) {
    validateFeedExists(feedId);
    if (feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId)) {
      return;
    }
    if (!saveFeedLike(feedId, currentUserId)) {
      return;
    }
    increaseLikeCount(feedId);
    log.info("피드 좋아요 완료: feedId={}", feedId);

    publishFeedLikedNotification(feedId, currentUserId);
  }

  @Transactional
  public void unlike(UUID feedId, UUID currentUserId) {
    validateFeedExists(feedId);
    if (feedLikeRepository.deleteByFeedIdAndUserId(feedId, currentUserId) > 0) {
      decreaseLikeCount(feedId);
      log.info("피드 좋아요 취소 완료: feedId={}", feedId);
    }
  }

  @Transactional
  public FeedDto update(UUID feedId, FeedUpdateRequest request, UUID currentUserId) {
    Feed feed = findActiveFeedOwnedBy(feedId, currentUserId);

    feed.updateContent(request.content());
    log.info("피드 수정 완료: feedId={}", feedId);

    UserSummary author = userSummaryQueryRepository.findByUserId(feed.getAuthorId());
    boolean likedByMe = feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId);

    eventPublisher.publishEvent(FeedIndexRequestedEvent.upsert(feedId));

    return feedMapper.toDto(feed, author, likedByMe);
  }

  @Transactional
  public void delete(UUID feedId, UUID currentUserId) {
    Feed feed = findActiveFeedOwnedBy(feedId, currentUserId);

    feed.delete();
    log.info("피드 삭제 완료: feedId={}", feedId);

    eventPublisher.publishEvent(FeedIndexRequestedEvent.delete(feedId));
  }

  private Feed findActiveFeedOwnedBy(UUID feedId, UUID currentUserId) {
    Feed feed = feedRepository.findById(feedId)
        .filter(f -> !f.isDeleted())
        .orElseThrow(() -> FeedNotFoundException.withId(feedId));
    validateAuthor(feed, currentUserId);
    return feed;
  }

  private void validateAuthor(Feed feed, UUID currentUserId) {
    if (!feed.getAuthorId().equals(currentUserId)) {
      throw FeedForbiddenException.authorMismatch();
    }
  }

  private Feed createFeedWithSnapshots(FeedCreateRequest request, UUID currentUserId) {
    WeatherSnapshot weatherSnapshot = weatherSnapshotProvider.readSnapshot(request.weatherId());
    List<OotdSnapshot> ootdSnapshots =
        ootdSnapshotProvider.readOotds(request.clothesIds(), currentUserId);
    return Feed.create(currentUserId, request.weatherId(), request.content(),
        weatherSnapshot, ootdSnapshots);
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

  // 저장 성공 시 true, 동시성 충돌로 이미 존재하면 false
  private boolean saveFeedLike(UUID feedId, UUID currentUserId) {
    try {
      FeedLike saved = feedLikeRepository.saveAndFlush(FeedLike.create(feedId, currentUserId));
      log.info("피드 좋아요 저장 완료: feedLikeId={}", saved.getId());
      return true;
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) {
        log.warn("피드 좋아요 중 동시성 충돌 발생: feedId={}", feedId);
        return false;
      }
      throw e;
    }
  }

  private void validateFeedExists(UUID feedId) {
    if (!feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)) {
      throw FeedNotFoundException.withId(feedId);
    }
  }

  private void increaseLikeCount(UUID feedId) {
    if (feedRepository.incrementLikeCount(feedId) != 1) {
      throw FeedNotFoundException.withId(feedId);
    }
  }

  private void decreaseLikeCount(UUID feedId) {
    if (feedRepository.decrementLikeCount(feedId) != 1) {
      log.warn("좋아요 취소 후 카운터 감소 실패: feedId={}", feedId);
    }
  }

  private void validateAuthorMatchesCurrentUser(UUID authorId, UUID currentUserId) {
    if (!authorId.equals(currentUserId)) {
      throw FeedForbiddenException.authorMismatch();
    }
  }

  private void publishFeedCreatedNotification(UUID authorId, String authorName, String content) {
    List<UUID> followerIds = followRepository.findFollowerIds(authorId);
    if (followerIds.isEmpty()) {
      return;
    }
    eventPublisher.publishEvent(new NotificationRequestedEvent(
        Set.copyOf(followerIds), authorName + "님이 새로운 피드를 작성했어요.",
        content, NotificationLevel.INFO));
  }

  private void publishFeedLikedNotification(UUID feedId, UUID likerId) {
    UUID authorId = feedRepository.findAuthorId(feedId)
        .orElseThrow(() -> FeedNotFoundException.withId(feedId));
    UserSummary liker = userSummaryQueryRepository.findByUserId(likerId);
    eventPublisher.publishEvent(new NotificationRequestedEvent(
        Set.of(authorId), "좋아요",
        liker.name() + "님이 내 피드를 좋아합니다.", NotificationLevel.INFO));
  }

  private boolean isUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException cve
        && UQ_FEED_LIKES.equalsIgnoreCase(cve.getConstraintName());
  }
}
