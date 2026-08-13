package com.sprint.mission.otboo.domain.social.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.authuser.user.exception.UserNotFoundException;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedUpdateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdDto;
import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import com.sprint.mission.otboo.domain.social.feed.exception.AuthorNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedForbiddenException;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.exception.WeatherNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.mapper.FeedMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedLikeRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.social.follow.repository.FollowRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.PrecipitationDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.TemperatureDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.dto.WeatherSummaryDto;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedService")
class FeedServiceTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  @InjectMocks
  FeedService feedService;

  @Mock
  FeedRepository feedRepository;

  @Mock
  FeedMapper feedMapper;

  @Mock
  UserSummaryQueryRepository userSummaryQueryRepository;

  @Mock
  WeatherSnapshotProvider weatherSnapshotProvider;

  @Mock
  OotdSnapshotProvider ootdSnapshotProvider;

  @Mock
  FeedLikeRepository feedLikeRepository;

  @Mock
  ApplicationEventPublisher eventPublisher;

  @Mock
  FollowRepository followRepository;

  private static void setFeedId(Feed feed, UUID id) {
    try {
      var field = Feed.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(feed, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setSoftDeletableNull(Feed feed) {
    try {
      var field = Feed.class.getDeclaredField("softDeletable");
      field.setAccessible(true);
      field.set(feed, null);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  @DisplayName("피드 등록")
  class CreateFeed {

    @Test
    @DisplayName("작성자 ID가 인증 사용자와 다르면 FeedForbiddenException을 던진다")
    void 작성자_ID가_인증_사용자와_다르면_FeedForbiddenException을_던진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", UUID.randomUUID())
          .sample();

      // when & then
      assertThatThrownBy(() -> feedService.create(request, currentUserId))
          .isInstanceOf(FeedForbiddenException.class)
          .satisfies(ex -> {
            FeedForbiddenException fe = (FeedForbiddenException) ex;
            assertThat(fe.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(fe.getDetails()).isEmpty();
          });
    }

    @Test
    @DisplayName("정상 요청이면 피드를 저장하고 UserSummary를 조회하여 FeedDto를 반환한다")
    void 정상_요청이면_피드를_저장하고_UserSummary를_조회하여_FeedDto를_반환한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      UserSummary mockAuthor = new UserSummary(currentUserId, "작성자명", "img.png");
      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(DUMMY_SNAPSHOT);
      given(userSummaryQueryRepository.findByUserId(currentUserId)).willReturn(mockAuthor);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));

      FeedDto expected = new FeedDto(
          UUID.randomUUID(), null, null, mockAuthor, null, null, request.content(), 0L, 0, false);
      when(feedMapper.toDto(any(Feed.class), eq(mockAuthor), eq(false))).thenReturn(expected);

      // when
      FeedDto result = feedService.create(request, currentUserId);

      // then
      ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
      verify(feedRepository).save(captor.capture());
      verify(userSummaryQueryRepository).findByUserId(currentUserId);

      Feed saved = captor.getValue();
      assertThat(saved.getAuthorId()).isEqualTo(currentUserId);
      assertThat(saved.getWeatherId()).isEqualTo(request.weatherId());
      assertThat(saved.getContent()).isEqualTo(request.content());
      assertThat(saved.getLikeCount()).isZero();
      assertThat(saved.getCommentCount()).isZero();
      assertThat(result).isEqualTo(expected);
      assertThat(result.author()).isEqualTo(mockAuthor);
      assertThat(result.author().name()).isEqualTo("작성자명");
    }

    @Test
    @DisplayName("작성자 정보가 존재하지 않으면 UserNotFoundException을 전파한다")
    void 작성자_정보가_존재하지_않으면_UserNotFoundException을_전파한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(DUMMY_SNAPSHOT);
      when(ootdSnapshotProvider.readOotds(any(), any())).thenReturn(List.of());
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(userSummaryQueryRepository.findByUserId(currentUserId))
          .thenThrow(UserNotFoundException.withNone());

      // when & then
      assertThatThrownBy(() -> feedService.create(request, currentUserId))
          .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("정상 등록 시 WeatherSnapshot이 Feed 스냅샷 컬럼에 저장된다")
    void 정상_등록_시_WeatherSnapshot이_Feed_스냅샷_컬럼에_저장된다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      UserSummary author = new UserSummary(currentUserId, "테스터", null);

      when(weatherSnapshotProvider.readSnapshot(request.weatherId())).thenReturn(DUMMY_SNAPSHOT);
      when(userSummaryQueryRepository.findByUserId(currentUserId)).thenReturn(author);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(feedMapper.toDto(any(Feed.class), eq(author), eq(false)))
          .thenReturn(new FeedDto(UUID.randomUUID(), null, null, author, null, null,
              request.content(), 0L, 0, false));

      // when
      feedService.create(request, currentUserId);

      // then
      ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
      verify(feedRepository).save(captor.capture());
      verify(weatherSnapshotProvider).readSnapshot(request.weatherId());
      Feed saved = captor.getValue();
      assertThat(saved.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(saved.getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
      assertThat(saved.getTemperatureCurrent()).isEqualTo(28.0);
      assertThat(saved.getTemperatureMin()).isEqualTo(16.0);
      assertThat(saved.getTemperatureMax()).isEqualTo(31.0);
    }

    @Test
    @DisplayName("존재하지 않는 weatherId면 WeatherNotFoundException을 던진다")
    void 존재하지_않는_weatherId면_WeatherNotFoundException을_던진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      when(weatherSnapshotProvider.readSnapshot(request.weatherId()))
          .thenThrow(WeatherNotFoundException.withId(request.weatherId()));

      // when & then
      assertThatThrownBy(() -> feedService.create(request, currentUserId))
          .isInstanceOf(WeatherNotFoundException.class)
          .satisfies(ex -> {
            WeatherNotFoundException e = (WeatherNotFoundException) ex;
            assertThat(e.getStatus().value()).isEqualTo(400);
            assertThat(e.getDetails()).containsEntry("weatherId",
                request.weatherId());
          });
    }

    @Test
    @DisplayName("정상 등록 시 반환 FeedDto.weather에 WeatherSummaryDto가 채워진다")
    void 정상_등록_시_반환_FeedDto_weather에_WeatherSummaryDto가_채워진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      UserSummary author = new UserSummary(currentUserId, "테스터", null);

      WeatherSummaryDto expectedWeather = new WeatherSummaryDto(
          request.weatherId(), SkyStatus.CLEAR,
          new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
          new TemperatureDto(28.0, 2.0, 16.0, 31.0));
      FeedDto expected = new FeedDto(
          UUID.randomUUID(), null, null, author, expectedWeather, null,
          request.content(), 0L, 0, false);

      when(weatherSnapshotProvider.readSnapshot(request.weatherId())).thenReturn(DUMMY_SNAPSHOT);
      when(userSummaryQueryRepository.findByUserId(currentUserId)).thenReturn(author);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(feedMapper.toDto(any(Feed.class), eq(author), eq(false))).thenReturn(expected);

      // when
      FeedDto result = feedService.create(request, currentUserId);

      // then
      assertThat(result.weather()).isNotNull();
      assertThat(result.weather().skyStatus()).isEqualTo(SkyStatus.CLEAR);
      assertThat(result.weather().temperature().min()).isEqualTo(16.0);
      assertThat(result.weather().temperature().max()).isEqualTo(31.0);
    }

    @Test
    @DisplayName("정상 등록 시 clothesIds로 조회한 OotdSnapshot이 Feed에 저장된다")
    void 정상_등록_시_clothesIds로_조회한_OotdSnapshot이_Feed에_저장된다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      OotdSnapshot ootd1 = fm.giveMeBuilder(OotdSnapshot.class)
          .set("name", "패딩")
          .sample();
      OotdSnapshot ootd2 = fm.giveMeBuilder(OotdSnapshot.class)
          .set("name", "청바지")
          .sample();
      List<OotdSnapshot> ootds = List.of(ootd1, ootd2);

      UserSummary author = new UserSummary(currentUserId, "테스터", null);

      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(DUMMY_SNAPSHOT);
      when(ootdSnapshotProvider.readOotds(request.clothesIds(), currentUserId))
          .thenReturn(ootds);
      when(userSummaryQueryRepository.findByUserId(currentUserId)).thenReturn(author);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(feedMapper.toDto(any(Feed.class), eq(author), eq(false)))
          .thenReturn(new FeedDto(UUID.randomUUID(), null, null, author, null,
              null, "내용", 0L, 0, false));

      // when
      feedService.create(request, currentUserId);

      // then
      ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
      verify(feedRepository).save(captor.capture());
      verify(ootdSnapshotProvider).readOotds(request.clothesIds(), currentUserId);
      Feed saved = captor.getValue();
      assertThat(saved.getOotds()).hasSize(2);
      assertThat(saved.getOotds().get(0).name()).isEqualTo("패딩");
      assertThat(saved.getOotds().get(1).name()).isEqualTo("청바지");
    }

    @Test
    @DisplayName("정상 등록 시 반환 FeedDto.ootds에 착장 정보가 채워진다")
    void 정상_등록_시_반환_FeedDto_ootds에_착장_정보가_채워진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      OotdSnapshot ootdSnapshot = fm.giveMeBuilder(OotdSnapshot.class)
          .set("name", "패딩")
          .sample();
      OotdDto ootdDto = fm.giveMeBuilder(OotdDto.class)
          .set("name", "패딩")
          .sample();
      UserSummary author = new UserSummary(currentUserId, "테스터", null);
      FeedDto expected = new FeedDto(
          UUID.randomUUID(), null, null, author, null, List.of(ootdDto), "내용", 0L, 0, false);

      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(DUMMY_SNAPSHOT);
      when(ootdSnapshotProvider.readOotds(request.clothesIds(), currentUserId))
          .thenReturn(List.of(ootdSnapshot));
      when(userSummaryQueryRepository.findByUserId(currentUserId)).thenReturn(author);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(feedMapper.toDto(any(Feed.class), eq(author), eq(false))).thenReturn(expected);

      // when
      FeedDto result = feedService.create(request, currentUserId);

      // then
      assertThat(result.ootds()).hasSize(1);
      assertThat(result.ootds().get(0).name()).isEqualTo("패딩");
    }

    @Test
    @DisplayName("피드를 등록하면 작성자의 팔로워들에게 알림 이벤트를 발행한다")
    void 피드를_등록하면_작성자의_팔로워들에게_알림_이벤트를_발행한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID follower1 = UUID.randomUUID();
      UUID follower2 = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .set("content", "오늘의 착장")
          .sample();

      UserSummary author = new UserSummary(currentUserId, "테스터", null);

      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(DUMMY_SNAPSHOT);
      when(userSummaryQueryRepository.findByUserId(currentUserId)).thenReturn(author);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(followRepository.findFollowerIds(currentUserId))
          .thenReturn(List.of(follower1, follower2));

      // when
      feedService.create(request, currentUserId);

      // then
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      NotificationRequestedEvent event = captor.getValue();
      assertThat(event.receiverIds()).containsExactlyInAnyOrder(follower1, follower2);
      assertThat(event.title()).isEqualTo("테스터님이 새로운 피드를 작성했어요.");
      assertThat(event.content()).isEqualTo("오늘의 착장");
    }

    @Test
    @DisplayName("팔로워가 없으면 알림 이벤트를 발행하지 않는다")
    void 팔로워가_없으면_알림_이벤트를_발행하지_않는다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      UserSummary author = new UserSummary(currentUserId, "테스터", null);

      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(DUMMY_SNAPSHOT);
      when(userSummaryQueryRepository.findByUserId(currentUserId)).thenReturn(author);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(followRepository.findFollowerIds(currentUserId)).thenReturn(List.of());

      // when
      feedService.create(request, currentUserId);

      // then
      verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("날씨 스냅샷이 없으면 NullPointerException을 던진다")
    void 날씨_스냅샷이_없으면_NullPointerException을_던진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      when(weatherSnapshotProvider.readSnapshot(any())).thenReturn(null);
      lenient().when(feedRepository.save(any(Feed.class)))
          .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(userSummaryQueryRepository.findByUserId(currentUserId))
          .thenReturn(new UserSummary(currentUserId, "테스터", null));

      // when & then
      assertThatThrownBy(() -> feedService.create(request, currentUserId))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("피드 목록 조회")
  class GetFeeds {

    @Test
    @DisplayName("Repository가 준 페이지를 FeedDto로 변환해 반환한다")
    void Repository가_준_페이지를_FeedDto로_변환해_반환한다() {
      // given
      FeedListParams params = new FeedListParams(
          null, null, 2,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      UUID authorId1 = UUID.randomUUID();
      UUID authorId2 = UUID.randomUUID();
      UserSummary summary1 = new UserSummary(authorId1, "유저1", "img1.png");
      UserSummary summary2 = new UserSummary(authorId2, "유저2", "img2.png");

      Feed feed1 = Feed.create(authorId1, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId2, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(feed1, feed2), "커서값", feed2.getId(), true, 2L,
          "createdAt", SortDirection.DESCENDING);
      when(feedRepository.findFeeds(params)).thenReturn(repoPage);
      given(userSummaryQueryRepository.findByUserIds(anyList()))
          .willReturn(List.of(summary1, summary2));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(feed1.getId(), null, null, summary1, null, null, "피드1", 0L, 0,
          false);
      FeedDto dto2 = new FeedDto(feed2.getId(), null, null, summary2, null, null, "피드2", 0L, 0,
          false);
      when(feedMapper.toDto(feed1, summary1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, summary2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result = feedService.getFeeds(params, UUID.randomUUID());

      // then
      assertThat(result.data()).containsExactly(dto1, dto2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.nextCursor()).isEqualTo("커서값");
      assertThat(result.nextIdAfter()).isEqualTo(feed2.getId());
    }

    @Test
    @DisplayName("Repository가 마지막 페이지를 주면 hasNext false와 null 커서를 그대로 전달한다")
    void Repository가_마지막_페이지를_주면_hasNext_false와_null_커서를_그대로_전달한다() {
      // given
      FeedListParams params = new FeedListParams(
          null, null, 5,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      UUID authorId1 = UUID.randomUUID();
      UUID authorId2 = UUID.randomUUID();
      UserSummary summary1 = new UserSummary(authorId1, "유저1", "img1.png");
      UserSummary summary2 = new UserSummary(authorId2, "유저2", "img2.png");

      Feed feed1 = Feed.create(authorId1, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId2, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(feed1, feed2), null, null, false, 2L,
          "createdAt", SortDirection.DESCENDING);
      when(feedRepository.findFeeds(params)).thenReturn(repoPage);
      given(userSummaryQueryRepository.findByUserIds(anyList()))
          .willReturn(List.of(summary1, summary2));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(feed1.getId(), null, null, summary1, null, null, "피드1", 0L, 0,
          false);
      FeedDto dto2 = new FeedDto(feed2.getId(), null, null, summary2, null, null, "피드2", 0L, 0,
          false);
      when(feedMapper.toDto(feed1, summary1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, summary2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result = feedService.getFeeds(params, UUID.randomUUID());

      // then
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();
      assertThat(result.data()).containsExactly(dto1, dto2);
    }

    @Test
    @DisplayName("여러 author를 findByUserIds 1회로 배치 조회하여 채운다")
    void 여러_author를_findByUserIds_1회로_배치_조회하여_채운다() {
      // given
      FeedListParams params = new FeedListParams(
          null, null, 2,
          FeedSortBy.CREATED_AT, SortDirection.DESCENDING,
          null, null);

      UUID authorId1 = UUID.randomUUID();
      UUID authorId2 = UUID.randomUUID();

      Feed feed1 = Feed.create(authorId1, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId2, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(feed1, feed2), null, null, false, 2L,
          "createdAt", SortDirection.DESCENDING);
      when(feedRepository.findFeeds(params)).thenReturn(repoPage);

      UserSummary summary1 = new UserSummary(authorId1, "유저1", "img1.png");
      UserSummary summary2 = new UserSummary(authorId2, "유저2", "img2.png");
      given(userSummaryQueryRepository.findByUserIds(anyList()))
          .willReturn(List.of(summary1, summary2));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(feed1.getId(), null, null, summary1, null, null, "피드1", 0L, 0,
          false);
      FeedDto dto2 = new FeedDto(feed2.getId(), null, null, summary2, null, null, "피드2", 0L, 0,
          false);
      when(feedMapper.toDto(feed1, summary1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, summary2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result = feedService.getFeeds(params, UUID.randomUUID());

      // then
      verify(userSummaryQueryRepository).findByUserIds(anyList());
      assertThat(result.data()).containsExactly(dto1, dto2);
    }

    @Test
    @DisplayName("내가 좋아요한 피드는 likedByMe=true로 반환한다")
    void 내가_좋아요한_피드는_likedByMe_true로_반환한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedListParams params = new FeedListParams(
          null, null, 2, FeedSortBy.CREATED_AT, SortDirection.DESCENDING, null, null);

      UUID authorId = UUID.randomUUID();
      UserSummary summary = new UserSummary(authorId, "유저", "img.png");

      UUID likedFeedId = UUID.randomUUID();
      UUID notLikedFeedId = UUID.randomUUID();
      Feed likedFeed = Feed.create(authorId, UUID.randomUUID(), "좋아요한 피드", DUMMY_SNAPSHOT,
          List.of());
      Feed notLikedFeed = Feed.create(authorId, UUID.randomUUID(), "안 누른 피드", DUMMY_SNAPSHOT,
          List.of());
      setFeedId(likedFeed, likedFeedId);
      setFeedId(notLikedFeed, notLikedFeedId);

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(likedFeed, notLikedFeed), null, null, false, 2L,
          "createdAt", SortDirection.DESCENDING);

      FeedDto likedDto = new FeedDto(likedFeedId, null, null, summary, null, null,
          "좋아요한 피드", 0L, 0, true);
      FeedDto notLikedDto = new FeedDto(notLikedFeedId, null, null, summary, null, null,
          "안 누른 피드", 0L, 0, false);

      when(feedRepository.findFeeds(params)).thenReturn(repoPage);
      when(userSummaryQueryRepository.findByUserIds(anyList())).thenReturn(List.of(summary));
      when(feedLikeRepository.findLikedFeedIds(any(), anyList()))
          .thenReturn(List.of(likedFeedId));
      when(feedMapper.toDto(likedFeed, summary, true)).thenReturn(likedDto);
      when(feedMapper.toDto(notLikedFeed, summary, false)).thenReturn(notLikedDto);

      // when
      CursorPageResponse<FeedDto> result = feedService.getFeeds(params, currentUserId);

      // then
      assertThat(result.data()).containsExactly(likedDto, notLikedDto);
    }

    @Test
    @DisplayName("작성자를 조회할 수 없으면 AuthorNotFoundException을 던진다")
    void 작성자를_조회할_수_없으면_AuthorNotFoundException을_던진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      Feed feed = Feed.create(authorId, UUID.randomUUID(), "내용", DUMMY_SNAPSHOT, List.of());
      setFeedId(feed, UUID.randomUUID());

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(feed), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);
      FeedListParams params = new FeedListParams(
          null, null, 2, FeedSortBy.CREATED_AT, SortDirection.DESCENDING, null, null);

      given(feedRepository.findFeeds(params)).willReturn(repoPage);

      // author 조회 결과 null
      given(userSummaryQueryRepository.findByUserIds(List.of(authorId)))
          .willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> feedService.getFeeds(params, currentUserId))
          .isInstanceOf(AuthorNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("피드 좋아요")
  class Like {

    @Test
    @DisplayName("피드가 존재하고 아직 좋아요하지 않았으면 좋아요를 저장하고 카운트를 증가시킨다")
    void 피드가_존재하고_아직_좋아요하지_않았으면_좋아요를_저장하고_카운트를_증가시킨다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);
      given(feedRepository.findAuthorId(feedId)).willReturn(Optional.of(UUID.randomUUID()));
      given(userSummaryQueryRepository.findByUserId(userId))
          .willReturn(new UserSummary(userId, "좋아요누른사람", "img.png"));
      given(feedLikeRepository.saveAndFlush(any(FeedLike.class))).willAnswer(
          inv -> inv.getArgument(0));
      given(feedRepository.incrementLikeCount(feedId)).willReturn(1);

      // when
      feedService.like(feedId, userId);

      // then
      ArgumentCaptor<FeedLike> captor = ArgumentCaptor.forClass(FeedLike.class);
      verify(feedLikeRepository).saveAndFlush(captor.capture());
      assertThat(captor.getValue().getFeedId()).isEqualTo(feedId);
      assertThat(captor.getValue().getUserId()).isEqualTo(userId);
      verify(feedRepository).incrementLikeCount(feedId);
    }

    @Test
    @DisplayName("이미 좋아요한 상태면 저장과 카운트 증가를 하지 않는다")
    void 이미_좋아요한_상태면_저장과_카운트_증가를_하지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(true);

      // when
      feedService.like(feedId, userId);

      // then
      verify(feedLikeRepository, never()).saveAndFlush(any());
      verify(feedRepository, never()).incrementLikeCount(any());
    }

    @Test
    @DisplayName("저장 중 동시 좋아요로 UQ 위반이 나면 예외를 삼키고 카운트를 증가시키지 않는다")
    void 저장_중_동시_좋아요로_UQ_위반이_나면_예외를_삼키고_카운트를_증가시키지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);

      ConstraintViolationException cause =
          new ConstraintViolationException("UQ 위반", null, "UQ_feed_likes_feed_id_user_id");
      given(feedLikeRepository.saveAndFlush(any()))
          .willThrow(new DataIntegrityViolationException("UQ 위반", cause));

      // when & then
      assertThatCode(() -> feedService.like(feedId, userId)).doesNotThrowAnyException();
      verify(feedRepository, never()).incrementLikeCount(any());
    }

    @Test
    @DisplayName("UQ가 아닌 제약 위반이면 삼키지 않고 전파한다")
    void UQ가_아닌_제약_위반이면_삼키지_않고_전파한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);

      // 원인 제약명이 UQ_feed_likes_feed_id_user_id 가 아닌 다른 제약
      ConstraintViolationException cause =
          new ConstraintViolationException("FK 위반", null, "FK_feeds_TO_feed_likes_1");
      given(feedLikeRepository.saveAndFlush(any()))
          .willThrow(new DataIntegrityViolationException("FK 위반", cause));

      // when & then
      assertThatThrownBy(() -> feedService.like(feedId, userId))
          .isInstanceOf(DataIntegrityViolationException.class);
      verify(feedRepository, never()).incrementLikeCount(any());
    }

    @Test
    @DisplayName("피드가 존재하지 않으면 FeedNotFoundException을 던진다")
    void 피드가_존재하지_않으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> feedService.like(feedId, userId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("좋아요에 성공하면 피드 작성자에게 알림 이벤트를 발행한다")
    void 좋아요에_성공하면_피드_작성자에게_알림_이벤트를_발행한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);
      given(feedRepository.findAuthorId(feedId)).willReturn(Optional.of(authorId));
      given(userSummaryQueryRepository.findByUserId(userId))
          .willReturn(new UserSummary(userId, "좋아요누른사람", "img.png"));
      given(feedLikeRepository.saveAndFlush(any(FeedLike.class))).willAnswer(
          inv -> inv.getArgument(0));
      given(feedRepository.incrementLikeCount(feedId)).willReturn(1);

      // when
      feedService.like(feedId, userId);

      // then
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      NotificationRequestedEvent event = captor.getValue();
      assertThat(event.receiverIds()).containsExactly(authorId);
      assertThat(event.content()).contains("좋아요누른사람");
    }

    @Test
    @DisplayName("카운트 증가가 반영되지 않으면 FeedNotFoundException을 던진다")
    void 카운트_증가가_반영되지_않으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);
      given(feedLikeRepository.saveAndFlush(any(FeedLike.class)))
          .willAnswer(inv -> inv.getArgument(0));
      given(feedRepository.incrementLikeCount(feedId)).willReturn(0);

      // when & then
      assertThatThrownBy(() -> feedService.like(feedId, userId))
          .isInstanceOf(FeedNotFoundException.class);
      verify(eventPublisher, never()).publishEvent(any());
    }
  }

  @Nested
  @DisplayName("피드 좋아요 취소")
  class Unlike {

    @Test
    @DisplayName("피드가 존재하고 좋아요가 있었으면 삭제하고 카운트를 감소시킨다")
    void 피드가_존재하고_좋아요가_있었으면_삭제하고_카운트를_감소시킨다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId)).willReturn(1L);
      given(feedRepository.decrementLikeCount(feedId)).willReturn(1);

      // when
      feedService.unlike(feedId, userId);

      // then
      verify(feedLikeRepository).deleteByFeedIdAndUserId(feedId, userId);
      verify(feedRepository).decrementLikeCount(feedId);
    }

    @Test
    @DisplayName("좋아요가 없었으면 카운트를 감소시키지 않는다")
    void 좋아요가_없었으면_카운트를_감소시키지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId)).willReturn(0L);

      // when
      feedService.unlike(feedId, userId);

      // then
      verify(feedRepository, never()).decrementLikeCount(any());
    }

    @Test
    @DisplayName("피드가 존재하지 않으면 FeedNotFoundException을 던진다")
    void 피드가_존재하지_않으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> feedService.unlike(feedId, userId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("카운트 감소가 반영되지 않아도 예외를 던지지 않는다")
    void 카운트_감소가_반영되지_않아도_예외를_던지지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId)).willReturn(1L);
      given(feedRepository.decrementLikeCount(feedId)).willReturn(0);

      // when & then
      assertThatCode(() -> feedService.unlike(feedId, userId)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("피드 수정")
  class UpdateFeed {

    @Test
    @DisplayName("내용을 수정하고 FeedDto를 반환한다")
    void 내용을_수정하고_FeedDto를_반환한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      Feed feed = Feed.create(authorId, UUID.randomUUID(), "원래 내용",
          DUMMY_SNAPSHOT, List.of());
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용");

      FeedDto expected = new FeedDto(
          feedId, Instant.now(), Instant.now(), null, null, List.of(),
          "수정된 내용", 0L, 0, false);
      given(feedMapper.toDto(eq(feed), any(), anyBoolean())).willReturn(expected);

      // when
      FeedDto result = feedService.update(feedId, request, authorId);

      // then
      assertThat(feed.getContent()).isEqualTo("수정된 내용");
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("반환하는 FeedDto에 작성자 정보를 채운다")
    void 반환하는_FeedDto에_작성자_정보를_채운다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      Feed feed = Feed.create(authorId, UUID.randomUUID(), "원래 내용",
          DUMMY_SNAPSHOT, List.of());
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      UserSummary author = new UserSummary(authorId, "경신", null);
      given(userSummaryQueryRepository.findByUserId(authorId)).willReturn(author);

      FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용");

      FeedDto expected = new FeedDto(
          feedId, Instant.now(), Instant.now(), author, null, List.of(),
          "수정된 내용", 0L, 0, false);
      given(feedMapper.toDto(eq(feed), eq(author), anyBoolean())).willReturn(expected);

      // when
      FeedDto result = feedService.update(feedId, request, authorId);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(result.author()).isEqualTo(author);
    }

    @Test
    @DisplayName("반환하는 FeedDto에 현재 사용자의 좋아요 여부를 채운다")
    void 반환하는_FeedDto에_현재_사용자의_좋아요_여부를_채운다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(currentUserId, UUID.randomUUID(), "원래 내용",
          DUMMY_SNAPSHOT, List.of());
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      UserSummary author = new UserSummary(currentUserId, "경신", null);
      given(userSummaryQueryRepository.findByUserId(currentUserId)).willReturn(author);
      given(feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId)).willReturn(true);

      FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용");

      FeedDto expected = new FeedDto(
          feedId, Instant.now(), Instant.now(), author, null, List.of(),
          "수정된 내용", 0L, 0, true);
      given(feedMapper.toDto(feed, author, true)).willReturn(expected);

      // when
      FeedDto result = feedService.update(feedId, request, currentUserId);

      // then
      assertThat(result).isEqualTo(expected);
      assertThat(result.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("작성자가 아니면 FeedForbiddenException을 던진다")
    void 작성자가_아니면_FeedForbiddenException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(authorId, UUID.randomUUID(), "원래 내용",
          DUMMY_SNAPSHOT, List.of());
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용");

      // when & then
      assertThatThrownBy(() -> feedService.update(feedId, request, currentUserId))
          .isInstanceOf(FeedForbiddenException.class);
    }

    @Test
    @DisplayName("이미 삭제된 피드면 FeedNotFoundException을 던진다")
    void 이미_삭제된_피드면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(currentUserId, UUID.randomUUID(), "원래 내용",
          DUMMY_SNAPSHOT, List.of());
      feed.delete();
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용");

      // when & then
      assertThatThrownBy(() -> feedService.update(feedId, request, currentUserId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("softDeletable이 null인 피드도 삭제되지 않은 것으로 처리한다")
    void softDeletable이_null인_피드도_삭제되지_않은_것으로_처리한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(currentUserId, UUID.randomUUID(), "원래 내용",
          DUMMY_SNAPSHOT, List.of());
      setSoftDeletableNull(feed);
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      UserSummary author = new UserSummary(currentUserId, "경신", null);
      given(userSummaryQueryRepository.findByUserId(currentUserId)).willReturn(author);

      FeedUpdateRequest request = new FeedUpdateRequest("수정된 내용");

      // when
      feedService.update(feedId, request, currentUserId);

      // then
      assertThat(feed.getContent()).isEqualTo("수정된 내용");
    }
  }

  @Nested
  @DisplayName("피드 삭제")
  class DeleteFeed {

    @Test
    @DisplayName("피드를 소프트 삭제한다")
    void 피드를_소프트_삭제한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(currentUserId, UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, List.of());
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      feedService.delete(feedId, currentUserId);

      // then
      assertThat(feed.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 삭제된 피드면 FeedNotFoundException을 던진다")
    void 이미_삭제된_피드면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(currentUserId, UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, List.of());
      feed.delete();
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when & then
      assertThatThrownBy(() -> feedService.delete(feedId, currentUserId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("피드가 존재하지 않으면 FeedNotFoundException을 던진다")
    void 피드가_존재하지_않으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      given(feedRepository.findById(feedId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> feedService.delete(feedId, currentUserId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("작성자가 아니면 FeedForbiddenException을 던진다")
    void 작성자가_아니면_FeedForbiddenException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(authorId, UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, List.of());
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when & then
      assertThatThrownBy(() -> feedService.delete(feedId, currentUserId))
          .isInstanceOf(FeedForbiddenException.class);
    }

    @Test
    @DisplayName("softDeletable이 null인 피드도 정상적으로 삭제한다")
    void softDeletable이_null인_피드도_정상적으로_삭제한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      Feed feed = Feed.create(currentUserId, UUID.randomUUID(), "내용",
          DUMMY_SNAPSHOT, List.of());
      setSoftDeletableNull(feed);
      given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

      // when
      feedService.delete(feedId, currentUserId);

      // then
      assertThat(feed.isDeleted()).isTrue();
    }
  }
}
