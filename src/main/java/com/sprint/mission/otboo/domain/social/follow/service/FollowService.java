package com.sprint.mission.otboo.domain.social.follow.service;

import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowCreateRequest;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowDto;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowSummaryDto;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowerListParams;
import com.sprint.mission.otboo.domain.social.follow.dto.FollowingListParams;
import com.sprint.mission.otboo.domain.social.follow.entity.Follow;
import com.sprint.mission.otboo.domain.social.follow.exception.FollowConflictException;
import com.sprint.mission.otboo.domain.social.follow.exception.FollowForbiddenException;
import com.sprint.mission.otboo.domain.social.follow.exception.FollowNotFoundException;
import com.sprint.mission.otboo.domain.social.follow.exception.FollowUserNotFoundException;
import com.sprint.mission.otboo.domain.social.follow.exception.SelfFollowNotAllowedException;
import com.sprint.mission.otboo.domain.social.follow.mapper.FollowMapper;
import com.sprint.mission.otboo.domain.social.follow.repository.FollowRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class FollowService {

  private static final String UQ_FOLLOWS = "uq_follows_follower_id_followee_id";

  private final FollowRepository followRepository;
  private final FollowMapper followMapper;
  private final UserSummaryQueryRepository userSummaryQueryRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public FollowDto create(FollowCreateRequest request, UUID currentUserId) {
    UUID followerId = request.followerId();
    UUID followeeId = request.followeeId();

    validateCreateRequest(followerId, followeeId, currentUserId);

    UserSummary follower = userSummaryQueryRepository.findByUserId(followerId);
    UserSummary followee = userSummaryQueryRepository.findByUserId(followeeId);

    Follow follow = findOrCreateFollow(followerId, followeeId, follower.name());

    return followMapper.toDto(follow, follower, followee);
  }

  private Follow findOrCreateFollow(UUID followerId, UUID followeeId, String followerName) {
    if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
      return findExistingFollow(followerId, followeeId);
    }
    try {
      Follow saved = followRepository.saveAndFlush(Follow.create(followerId, followeeId));
      log.info("팔로우 생성 완료: followId={}", saved.getId());
      publishFollowNotification(followeeId, followerName);
      return saved;
    } catch (DataIntegrityViolationException e) {
      if (isUniqueViolation(e)) {
        Follow existing = findExistingFollow(followerId, followeeId);
        log.warn("팔로우 생성 중 동시성 충돌 발생: followId={}", existing.getId());
        return existing;
      }
      throw e; // UQ 외 제약 위반은 전파
    }
  }

  public FollowSummaryDto getSummary(UUID userId, UUID currentUserId) {
    if (!userSummaryQueryRepository.existsByUserId(userId)) {
      throw UserNotFoundException.withNone();
    }

    long followerCount = followRepository.countByFolloweeId(userId);
    long followingCount = followRepository.countByFollowerId(userId);

    UUID followedByMeId = followRepository.findByFollowerIdAndFolloweeId(currentUserId, userId)
        .map(Follow::getId)
        .orElse(null);
    boolean followedByMe = followedByMeId != null;

    boolean followingMe = followRepository.existsByFollowerIdAndFolloweeId(userId, currentUserId);

    return followMapper.toSummaryDto(
        userId, followerCount, followingCount, followedByMe, followedByMeId, followingMe);
  }

  public CursorPageResponse<FollowDto> getFollowings(FollowingListParams params) {
    return toDtoPage(followRepository.findFollowings(params));
  }

  public CursorPageResponse<FollowDto> getFollowers(FollowerListParams params) {
    return toDtoPage(followRepository.findFollowers(params));
  }

  @Transactional
  public void delete(UUID followId, UUID currentUserId) {
    Follow follow = followRepository.findById(followId)
        .orElseThrow(() -> FollowNotFoundException.of(followId));

    if (!follow.getFollowerId().equals(currentUserId)) {
      throw FollowForbiddenException.notOwner(followId);
    }

    followRepository.delete(follow);
    log.info("팔로우 취소 완료: followId={}", followId);
  }

  private CursorPageResponse<FollowDto> toDtoPage(CursorPageResponse<Follow> page) {
    List<Follow> follows = page.data();
    Map<UUID, UserSummary> summaryMap = follows.isEmpty() ? Map.of() :
        userSummaryQueryRepository.findByUserIds(
                follows.stream()
                    .flatMap(f -> Stream.of(f.getFollowerId(), f.getFolloweeId()))
                    .distinct().toList()
            ).stream()
            .collect(Collectors.toMap(UserSummary::userId, Function.identity()));

    List<FollowDto> data = follows.stream()
        .map(f -> {
          UserSummary follower = summaryMap.get(f.getFollowerId());
          UserSummary followee = summaryMap.get(f.getFolloweeId());
          if (follower == null || followee == null) {
            log.warn("팔로우 사용자 정보를 조회할 수 없습니다: followId={}", f.getId());
            throw FollowUserNotFoundException.withNone();
          }
          return followMapper.toDto(f, follower, followee);
        })
        .toList();

    return new CursorPageResponse<>(data, page.nextCursor(), page.nextIdAfter(),
        page.hasNext(), page.totalCount(), page.sortBy(), page.sortDirection());
  }

  private void validateCreateRequest(UUID followerId, UUID followeeId, UUID currentUserId) {
    validateFollowerMatchesCurrentUser(followerId, currentUserId);
    validateNotSelfFollow(followerId, followeeId);
  }

  private void publishFollowNotification(UUID followeeId, String followerName) {
    String content = followerName + "님이 나를 팔로우했어요.";
    eventPublisher.publishEvent(
        new NotificationRequestedEvent(Set.of(followeeId), "팔로우", content, NotificationLevel.INFO));
  }

  private void validateFollowerMatchesCurrentUser(UUID followerId, UUID currentUserId) {
    if (!followerId.equals(currentUserId)) {
      throw FollowForbiddenException.followerMismatch();
    }
  }

  private void validateNotSelfFollow(UUID followerId, UUID followeeId) {
    if (followerId.equals(followeeId)) {
      throw SelfFollowNotAllowedException.withNone();
    }
  }

  private Follow findExistingFollow(UUID followerId, UUID followeeId) {
    return followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId)
        .orElseThrow(FollowConflictException::withNone);
  }

  private boolean isUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException cve
        && UQ_FOLLOWS.equalsIgnoreCase(cve.getConstraintName());
  }
}