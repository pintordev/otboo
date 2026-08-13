package com.sprint.mission.otboo.domain.social.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
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
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowService")
class FollowServiceTest {

  private static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  private FollowService followService;

  @Mock
  private FollowRepository followRepository;

  @Mock
  private FollowMapper followMapper;

  @Mock
  private UserSummaryQueryRepository userSummaryQueryRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  private DataIntegrityViolationException uniqueViolation(String constraintName) {
    ConstraintViolationException cause =
        new ConstraintViolationException("unique violation", null, constraintName);
    return new DataIntegrityViolationException("could not execute statement", cause);
  }

  @Nested
  @DisplayName("팔로우 등록")
  class CreateFollow {

    @Test
    @DisplayName("정상 요청이면 Follow를 저장하고 FollowDto를 반환한다")
    void 정상_요청이면_Follow를_저장하고_FollowDto를_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      Follow persistedFollow = Follow.create(followerId, followeeId);
      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      FollowDto expected = new FollowDto(persistedFollow.getId(), followeeSummary, followerSummary);

      given(followRepository.saveAndFlush(any(Follow.class))).willReturn(persistedFollow);
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId)).willReturn(followeeSummary);
      given(followMapper.toDto(eq(persistedFollow), eq(followerSummary), eq(followeeSummary)))
          .willReturn(expected);

      // when
      FollowDto result = followService.create(request, followerId);

      // then
      assertThat(result).isEqualTo(expected);

      ArgumentCaptor<Follow> followCaptor = ArgumentCaptor.forClass(Follow.class);
      verify(followRepository).saveAndFlush(followCaptor.capture());
      Follow savedFollow = followCaptor.getValue();
      assertThat(savedFollow.getFollowerId()).isEqualTo(followerId);
      assertThat(savedFollow.getFolloweeId()).isEqualTo(followeeId);

      verify(userSummaryQueryRepository).findByUserId(followerId);
      verify(userSummaryQueryRepository).findByUserId(followeeId);
    }

    @Test
    @DisplayName("자기 자신을 팔로우하면 SelfFollowNotAllowedException을 던진다")
    void 자기_자신을_팔로우하면_SelfFollowNotAllowedException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", userId)
          .set("followeeId", userId)
          .sample();

      // when & then
      assertThatThrownBy(() -> followService.create(request, userId))
          .isInstanceOf(SelfFollowNotAllowedException.class);
    }

    @Test
    @DisplayName("요청자가 인증 사용자와 다르면 FollowForbiddenException을 던진다")
    void 요청자가_인증_사용자와_다르면_FollowForbiddenException을_던진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      // when & then
      assertThatThrownBy(() -> followService.create(request, currentUserId))
          .isInstanceOf(FollowForbiddenException.class);
    }

    @Test
    @DisplayName("이미 팔로우 중이면 저장하지 않고 기존 Follow로 FollowDto를 반환한다")
    void 이미_팔로우_중이면_저장하지_않고_기존_Follow로_FollowDto를_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      Follow existing = Follow.create(followerId, followeeId);
      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      FollowDto expected = new FollowDto(existing.getId(), followeeSummary, followerSummary);

      given(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(true);
      given(followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(Optional.of(existing));
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId)).willReturn(followeeSummary);
      given(followMapper.toDto(eq(existing), eq(followerSummary), eq(followeeSummary)))
          .willReturn(expected);

      // when
      FollowDto result = followService.create(request, followerId);

      // then
      assertThat(result).isEqualTo(expected);
      verify(followRepository, never()).saveAndFlush(any(Follow.class));
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("동시 저장으로 유니크 제약이 위반되면 기존 Follow로 멱등 반환한다")
    void 동시_저장으로_유니크_제약이_위반되면_기존_Follow로_멱등_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      Follow existing = Follow.create(followerId, followeeId);
      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      FollowDto expected = new FollowDto(existing.getId(), followeeSummary, followerSummary);

      // exists는 false로 통과, saveAndFlush에서 UQ 위반
      given(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(false);
      given(followRepository.saveAndFlush(any(Follow.class)))
          .willThrow(uniqueViolation("uq_follows_follower_id_followee_id"));
      given(followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(Optional.of(existing));
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId)).willReturn(followeeSummary);
      given(followMapper.toDto(eq(existing), eq(followerSummary), eq(followeeSummary)))
          .willReturn(expected);

      // when
      FollowDto result = followService.create(request, followerId);

      // then
      assertThat(result).isEqualTo(expected);
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("멱등 경로에서 기존 팔로우를 찾지 못하면 FollowConflictException을 던진다")
    void 멱등_경로에서_기존_팔로우를_찾지_못하면_FollowConflictException을_던진다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId)).willReturn(followeeSummary);

      given(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(true);
      given(followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> followService.create(request, followerId))
          .isInstanceOf(FollowConflictException.class);
    }

    @Test
    @DisplayName("유니크 제약이 아닌 제약 위반이면 예외를 전파한다")
    void 유니크_제약이_아닌_제약_위반이면_예외를_전파한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId)).willReturn(followeeSummary);

      given(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId))
          .willReturn(false);
      given(followRepository.saveAndFlush(any(Follow.class)))
          .willThrow(uniqueViolation("fk_users_to_follows_1"));

      // when & then
      assertThatThrownBy(() -> followService.create(request, followerId))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("팔로우 생성에 성공하면 팔로위에게 알림 이벤트를 발행한다")
    void 팔로우_생성에_성공하면_팔로위에게_알림_이벤트를_발행한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId)
          .set("followeeId", followeeId)
          .sample();

      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId)
          .set("name", "이경신")
          .sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      FollowDto expected = new FollowDto(UUID.randomUUID(), followeeSummary, followerSummary);

      given(followRepository.saveAndFlush(any(Follow.class)))
          .willAnswer(inv -> inv.getArgument(0));
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId)).willReturn(followeeSummary);
      given(followMapper.toDto(any(Follow.class), eq(followerSummary), eq(followeeSummary)))
          .willReturn(expected);

      // when
      followService.create(request, followerId);

      // then
      ArgumentCaptor<NotificationRequestedEvent> eventCaptor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(eventCaptor.capture());

      NotificationRequestedEvent event = eventCaptor.getValue();
      assertThat(event.receiverIds()).containsExactly(followeeId);
      assertThat(event.content()).isEqualTo("이경신님이 나를 팔로우했어요.");
      assertThat(event.level()).isEqualTo(NotificationLevel.INFO);
    }

    @Test
    @DisplayName("팔로우 대상 유저가 없으면 UserNotFoundException을 전파한다")
    void 팔로우_대상_유저가_없으면_UserNotFoundException을_전파한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      FollowCreateRequest request = fm.giveMeBuilder(FollowCreateRequest.class)
          .set("followerId", followerId).set("followeeId", followeeId).sample();

      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      given(userSummaryQueryRepository.findByUserId(followerId)).willReturn(followerSummary);
      given(userSummaryQueryRepository.findByUserId(followeeId))
          .willThrow(UserNotFoundException.withNone());

      // when & then
      assertThatThrownBy(() -> followService.create(request, followerId))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("팔로우 취소")
  class DeleteFollow {

    @Test
    @DisplayName("존재하지 않는 followId면 FollowNotFoundException을 던진다")
    void 존재하지_않는_followId면_FollowNotFoundException을_던진다() {
      // given
      UUID followId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      given(followRepository.findById(followId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> followService.delete(followId, currentUserId))
          .isInstanceOf(FollowNotFoundException.class);
    }

    @Test
    @DisplayName("본인의 팔로우가 아니면 FollowForbiddenException을 던진다")
    void 본인의_팔로우가_아니면_FollowForbiddenException을_던진다() {
      // given
      UUID followId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      UUID otherFollowerId = UUID.randomUUID(); // 남의 팔로우
      Follow follow = Follow.create(otherFollowerId, UUID.randomUUID());
      given(followRepository.findById(followId)).willReturn(Optional.of(follow));

      // when & then
      assertThatThrownBy(() -> followService.delete(followId, currentUserId))
          .isInstanceOf(FollowForbiddenException.class);
    }

    @Test
    @DisplayName("본인의 팔로우면 하드 삭제한다")
    void 본인의_팔로우면_하드_삭제한다() {
      // given
      UUID followId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Follow follow = Follow.create(currentUserId, UUID.randomUUID());
      given(followRepository.findById(followId)).willReturn(Optional.of(follow));

      // when
      followService.delete(followId, currentUserId);

      // then
      verify(followRepository).delete(follow);
    }
  }

  @Nested
  @DisplayName("팔로우 요약 조회")
  class GetSummary {

    @Test
    @DisplayName("조회 대상 유저가 존재하지 않으면 UserNotFoundException을 던진다")
    void 조회_대상_유저가_존재하지_않으면_UserNotFoundException을_던진다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID me = UUID.randomUUID();
      given(userSummaryQueryRepository.existsByUserId(userId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> followService.getSummary(userId, me))
          .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("팔로우 요약 정보를 반환한다")
    void 팔로우_요약_정보를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID me = UUID.randomUUID();
      UUID followId = UUID.randomUUID();

      given(userSummaryQueryRepository.existsByUserId(userId)).willReturn(true);

      Follow follow = mock(Follow.class);
      given(follow.getId()).willReturn(followId);

      given(followRepository.countByFolloweeId(userId)).willReturn(10L);
      given(followRepository.countByFollowerId(userId)).willReturn(5L);
      given(followRepository.findByFollowerIdAndFolloweeId(me, userId)).willReturn(
          Optional.of(follow));
      given(followRepository.existsByFollowerIdAndFolloweeId(userId, me)).willReturn(false);

      FollowSummaryDto expected = new FollowSummaryDto(userId, 10L, 5L, true, followId, false);
      given(followMapper.toSummaryDto(userId, 10L, 5L, true, followId, false))
          .willReturn(expected);

      // when
      FollowSummaryDto result = followService.getSummary(userId, me);

      // then
      assertThat(result).isEqualTo(expected);
      verify(userSummaryQueryRepository).existsByUserId(userId);
    }

    @Test
    @DisplayName("내가 팔로우하지 않으면 followedByMe는 false, followedByMeId는 null이다")
    void 내가_팔로우하지_않으면_followedByMe는_false이고_followedByMeId는_null이다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID me = UUID.randomUUID();

      given(userSummaryQueryRepository.existsByUserId(userId)).willReturn(true);

      given(followRepository.countByFolloweeId(userId)).willReturn(0L);
      given(followRepository.countByFollowerId(userId)).willReturn(0L);
      given(followRepository.findByFollowerIdAndFolloweeId(me, userId)).willReturn(
          Optional.empty());
      given(followRepository.existsByFollowerIdAndFolloweeId(userId, me)).willReturn(false);

      FollowSummaryDto expected = new FollowSummaryDto(userId, 0L, 0L, false, null, false);
      given(followMapper.toSummaryDto(userId, 0L, 0L, false, null, false))
          .willReturn(expected);

      // when
      FollowSummaryDto result = followService.getSummary(userId, me);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(result.followedByMe()).isFalse();
      assertThat(result.followedByMeId()).isNull();
    }

    @Test
    @DisplayName("대상이 나를 팔로우하면 followingMe는 true이다")
    void 대상이_나를_팔로우하면_followingMe는_true이다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID me = UUID.randomUUID();

      given(userSummaryQueryRepository.existsByUserId(userId)).willReturn(true);

      given(followRepository.countByFolloweeId(userId)).willReturn(0L);
      given(followRepository.countByFollowerId(userId)).willReturn(0L);
      given(followRepository.findByFollowerIdAndFolloweeId(me, userId)).willReturn(
          Optional.empty());
      given(followRepository.existsByFollowerIdAndFolloweeId(userId, me)).willReturn(true);

      FollowSummaryDto expected = new FollowSummaryDto(userId, 0L, 0L, false, null, true);
      given(followMapper.toSummaryDto(userId, 0L, 0L, false, null, true))
          .willReturn(expected);

      // when
      FollowSummaryDto result = followService.getSummary(userId, me);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(result.followingMe()).isTrue();
    }
  }

  @Nested
  @DisplayName("팔로잉 조회")
  class GetFollowings {

    @Test
    @DisplayName("리포 결과를 배치 조회·매핑해 FollowDto 페이지로 반환한다")
    void 리포_결과를_배치_조회_매핑해_FollowDto_페이지로_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      Follow follow = Follow.create(followerId, followeeId);
      CursorPageResponse<Follow> repoPage = new CursorPageResponse<>(
          List.of(follow), "cursor", follow.getId(), false, 1L, "createdAt",
          SortDirection.DESCENDING);

      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      FollowDto dto = new FollowDto(follow.getId(), followeeSummary, followerSummary);

      FollowingListParams params = fm.giveMeBuilder(FollowingListParams.class)
          .set("followerId", followerId)
          .set("limit", 10)
          .set("cursor", null)
          .set("idAfter", null)
          .sample();
      given(followRepository.findFollowings(params)).willReturn(repoPage);
      given(userSummaryQueryRepository.findByUserIds(any()))
          .willReturn(List.of(followerSummary, followeeSummary));
      given(followMapper.toDto(follow, followerSummary, followeeSummary)).willReturn(dto);

      // when
      CursorPageResponse<FollowDto> result = followService.getFollowings(params);

      // then
      assertThat(result.data()).containsExactly(dto);
      assertThat(result.totalCount()).isEqualTo(1L);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isEqualTo("cursor");
      assertThat(result.nextIdAfter()).isEqualTo(follow.getId());
      assertThat(result.sortBy()).isEqualTo("createdAt");
      assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);

      ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
      verify(userSummaryQueryRepository).findByUserIds(idsCaptor.capture());
      assertThat(idsCaptor.getValue()).contains(followerId, followeeId);
      verify(userSummaryQueryRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("결과가 없으면 빈 데이터 페이지를 반환하고 배치 조회를 하지 않는다")
    void 결과가_없으면_빈_데이터_페이지를_반환하고_배치_조회를_하지_않는다() {
      // given
      FollowingListParams params = fm.giveMeBuilder(FollowingListParams.class)
          .set("followerId", UUID.randomUUID())
          .set("limit", 10)
          .set("cursor", null)
          .set("idAfter", null)
          .sample();
      CursorPageResponse<Follow> emptyPage = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING);
      given(followRepository.findFollowings(params)).willReturn(emptyPage);

      // when
      CursorPageResponse<FollowDto> result = followService.getFollowings(params);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.totalCount()).isEqualTo(0L);
      verify(userSummaryQueryRepository, never()).findByUserIds(any());
    }

    @Test
    @DisplayName("사용자 정보를 조회할 수 없으면 FollowUserNotFoundException을 던진다")
    void 사용자_정보를_조회할_수_없으면_FollowUserNotFoundException을_던진다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      Follow follow = Follow.create(followerId, followeeId);
      CursorPageResponse<Follow> repoPage = new CursorPageResponse<>(
          List.of(follow), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);

      FollowingListParams params = fm.giveMeBuilder(FollowingListParams.class)
          .set("followerId", followerId)
          .set("limit", 10)
          .set("cursor", null)
          .set("idAfter", null)
          .sample();
      given(followRepository.findFollowings(params)).willReturn(repoPage);
      given(userSummaryQueryRepository.findByUserIds(any())).willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> followService.getFollowings(params))
          .isInstanceOf(FollowUserNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("팔로워 조회")
  class GetFollowers {

    @Test
    @DisplayName("리포 결과를 배치 조회·매핑해 FollowDto 페이지로 반환한다")
    void 리포_결과를_배치_조회_매핑해_FollowDto_페이지로_반환한다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      Follow follow = Follow.create(followerId, followeeId);
      CursorPageResponse<Follow> repoPage = new CursorPageResponse<>(
          List.of(follow), "cursor", follow.getId(), false, 1L, "createdAt",
          SortDirection.DESCENDING);

      UserSummary followerSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followerId).sample();
      UserSummary followeeSummary = fm.giveMeBuilder(UserSummary.class)
          .set("userId", followeeId).sample();
      FollowDto dto = new FollowDto(follow.getId(), followeeSummary, followerSummary);

      FollowerListParams params = fm.giveMeBuilder(FollowerListParams.class)
          .set("followeeId", followeeId)
          .set("limit", 10)
          .set("cursor", null)
          .set("idAfter", null)
          .sample();
      given(followRepository.findFollowers(params)).willReturn(repoPage);
      given(userSummaryQueryRepository.findByUserIds(any()))
          .willReturn(List.of(followerSummary, followeeSummary));
      given(followMapper.toDto(follow, followerSummary, followeeSummary)).willReturn(dto);

      // when
      CursorPageResponse<FollowDto> result = followService.getFollowers(params);

      // then
      assertThat(result.data()).containsExactly(dto);
      assertThat(result.totalCount()).isEqualTo(1L);
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isEqualTo("cursor");
      assertThat(result.nextIdAfter()).isEqualTo(follow.getId());
      assertThat(result.sortBy()).isEqualTo("createdAt");
      assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING);

      ArgumentCaptor<Collection<UUID>> idsCaptor = ArgumentCaptor.forClass(Collection.class);
      verify(userSummaryQueryRepository).findByUserIds(idsCaptor.capture());
      assertThat(idsCaptor.getValue()).contains(followerId, followeeId);
      verify(userSummaryQueryRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("결과가 없으면 빈 데이터 페이지를 반환하고 배치 조회를 하지 않는다")
    void 결과가_없으면_빈_데이터_페이지를_반환하고_배치_조회를_하지_않는다() {
      // given
      FollowerListParams params = fm.giveMeBuilder(FollowerListParams.class)
          .set("followeeId", UUID.randomUUID())
          .set("limit", 10)
          .set("cursor", null)
          .set("idAfter", null)
          .sample();
      CursorPageResponse<Follow> emptyPage = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING);
      given(followRepository.findFollowers(params)).willReturn(emptyPage);

      // when
      CursorPageResponse<FollowDto> result = followService.getFollowers(params);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.totalCount()).isEqualTo(0L);
      verify(userSummaryQueryRepository, never()).findByUserIds(any());
    }

    @Test
    @DisplayName("사용자 정보를 조회할 수 없으면 FollowUserNotFoundException을 던진다")
    void 사용자_정보를_조회할_수_없으면_FollowUserNotFoundException을_던진다() {
      // given
      UUID followerId = UUID.randomUUID();
      UUID followeeId = UUID.randomUUID();
      Follow follow = Follow.create(followerId, followeeId);
      CursorPageResponse<Follow> repoPage = new CursorPageResponse<>(
          List.of(follow), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);

      FollowerListParams params = fm.giveMeBuilder(FollowerListParams.class)
          .set("followeeId", followeeId)
          .set("limit", 10)
          .set("cursor", null)
          .set("idAfter", null)
          .sample();
      given(followRepository.findFollowers(params)).willReturn(repoPage);
      given(userSummaryQueryRepository.findByUserIds(any())).willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> followService.getFollowers(params))
          .isInstanceOf(FollowUserNotFoundException.class);
    }
  }
}
