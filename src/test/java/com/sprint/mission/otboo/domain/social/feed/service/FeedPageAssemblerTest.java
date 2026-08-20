package com.sprint.mission.otboo.domain.social.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSearchResult;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.exception.AuthorNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.mapper.FeedMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedLikeRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedPageAssembler")
class FeedPageAssemblerTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);

  @InjectMocks
  FeedPageAssembler feedPageAssembler;

  @Mock
  FeedRepository feedRepository;

  @Mock
  UserSummaryQueryRepository userSummaryQueryRepository;

  @Mock
  FeedLikeRepository feedLikeRepository;

  @Mock
  FeedMapper feedMapper;

  private static void setFeedId(Feed feed, UUID id) {
    try {
      var field = Feed.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(feed, id);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private FeedListParams params(int limit) {
    return new FeedListParams(null, null, limit,
        FeedSortBy.CREATED_AT, SortDirection.DESCENDING, null, null, null, null);
  }

  @Nested
  @DisplayName("검색 결과 조립")
  class Assemble {

    @Test
    @DisplayName("검색 결과를 FeedDto로 변환해 반환한다")
    void 검색_결과를_FeedDto로_변환해_반환한다() {
      // given
      FeedListParams params = params(2);

      UUID authorId1 = UUID.randomUUID();
      UUID authorId2 = UUID.randomUUID();
      UserSummary summary1 = new UserSummary(authorId1, "유저1", "img1.png");
      UserSummary summary2 = new UserSummary(authorId2, "유저2", "img2.png");

      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      Feed feed1 = Feed.create(authorId1, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId2, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());
      setFeedId(feed1, id1);
      setFeedId(feed2, id2);

      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(id1, id2), 2L, "커서값", id2, true);
      given(feedRepository.findAllActiveByIds(List.of(id1, id2)))
          .willReturn(List.of(feed1, feed2));
      given(userSummaryQueryRepository.findByUserIds(anyList()))
          .willReturn(List.of(summary1, summary2));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(id1, null, null, summary1, null, null, "피드1", 0L, 0, false);
      FeedDto dto2 = new FeedDto(id2, null, null, summary2, null, null, "피드2", 0L, 0, false);
      when(feedMapper.toDto(feed1, summary1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, summary2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result =
          feedPageAssembler.assemble(searchResult, params, UUID.randomUUID());

      // then
      assertThat(result.data()).containsExactly(dto1, dto2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.nextCursor()).isEqualTo("커서값");
      assertThat(result.nextIdAfter()).isEqualTo(id2);
    }

    @Test
    @DisplayName("작성자를 조회할 수 없으면 AuthorNotFoundException을 던진다")
    void 작성자를_조회할_수_없으면_AuthorNotFoundException을_던진다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UUID feedId = UUID.randomUUID();
      Feed feed = Feed.create(authorId, UUID.randomUUID(), "내용", DUMMY_SNAPSHOT, List.of());
      setFeedId(feed, feedId);

      FeedListParams params = params(2);
      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(feedId), 1L, null, null, false);
      given(feedRepository.findAllActiveByIds(List.of(feedId))).willReturn(List.of(feed));

      // author 조회 결과 없음
      given(userSummaryQueryRepository.findByUserIds(List.of(authorId)))
          .willReturn(List.of());

      // when & then
      assertThatThrownBy(() -> feedPageAssembler.assemble(searchResult, params, currentUserId))
          .isInstanceOf(AuthorNotFoundException.class);
    }

    @Test
    @DisplayName("마지막 페이지면 hasNext false와 null 커서를 그대로 전달한다")
    void 마지막_페이지면_hasNext_false와_null_커서를_그대로_전달한다() {
      // given
      FeedListParams params = params(5);

      UUID authorId1 = UUID.randomUUID();
      UUID authorId2 = UUID.randomUUID();
      UserSummary summary1 = new UserSummary(authorId1, "유저1", "img1.png");
      UserSummary summary2 = new UserSummary(authorId2, "유저2", "img2.png");

      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      Feed feed1 = Feed.create(authorId1, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId2, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());
      setFeedId(feed1, id1);
      setFeedId(feed2, id2);

      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(id1, id2), 2L, null, null, false);
      given(feedRepository.findAllActiveByIds(List.of(id1, id2)))
          .willReturn(List.of(feed1, feed2));
      given(userSummaryQueryRepository.findByUserIds(anyList()))
          .willReturn(List.of(summary1, summary2));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(id1, null, null, summary1, null, null, "피드1", 0L, 0, false);
      FeedDto dto2 = new FeedDto(id2, null, null, summary2, null, null, "피드2", 0L, 0, false);
      when(feedMapper.toDto(feed1, summary1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, summary2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result =
          feedPageAssembler.assemble(searchResult, params, UUID.randomUUID());

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
      FeedListParams params = params(2);
      UUID authorId1 = UUID.randomUUID();
      UUID authorId2 = UUID.randomUUID();

      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      Feed feed1 = Feed.create(authorId1, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId2, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());
      setFeedId(feed1, id1);
      setFeedId(feed2, id2);

      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(id1, id2), 2L, null, null, false);
      given(feedRepository.findAllActiveByIds(List.of(id1, id2)))
          .willReturn(List.of(feed1, feed2));

      UserSummary summary1 = new UserSummary(authorId1, "유저1", "img1.png");
      UserSummary summary2 = new UserSummary(authorId2, "유저2", "img2.png");
      given(userSummaryQueryRepository.findByUserIds(anyList()))
          .willReturn(List.of(summary1, summary2));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(id1, null, null, summary1, null, null, "피드1", 0L, 0, false);
      FeedDto dto2 = new FeedDto(id2, null, null, summary2, null, null, "피드2", 0L, 0, false);
      when(feedMapper.toDto(feed1, summary1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, summary2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result =
          feedPageAssembler.assemble(searchResult, params, UUID.randomUUID());

      // then
      verify(userSummaryQueryRepository).findByUserIds(anyList());
      assertThat(result.data()).containsExactly(dto1, dto2);
    }

    @Test
    @DisplayName("내가 좋아요한 피드는 likedByMe=true로 반환한다")
    void 내가_좋아요한_피드는_likedByMe_true로_반환한다() {
      // given
      FeedListParams params = params(2);
      UUID currentUserId = UUID.randomUUID();
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

      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(likedFeedId, notLikedFeedId), 2L, null, null, false);
      given(feedRepository.findAllActiveByIds(List.of(likedFeedId, notLikedFeedId)))
          .willReturn(List.of(likedFeed, notLikedFeed));

      FeedDto likedDto = new FeedDto(likedFeedId, null, null, summary, null, null,
          "좋아요한 피드", 0L, 0, true);
      FeedDto notLikedDto = new FeedDto(notLikedFeedId, null, null, summary, null, null,
          "안 누른 피드", 0L, 0, false);

      when(userSummaryQueryRepository.findByUserIds(anyList())).thenReturn(List.of(summary));
      when(feedLikeRepository.findLikedFeedIds(any(), anyList()))
          .thenReturn(List.of(likedFeedId));
      when(feedMapper.toDto(likedFeed, summary, true)).thenReturn(likedDto);
      when(feedMapper.toDto(notLikedFeed, summary, false)).thenReturn(notLikedDto);

      // when
      CursorPageResponse<FeedDto> result =
          feedPageAssembler.assemble(searchResult, params, currentUserId);

      // then
      assertThat(result.data()).containsExactly(likedDto, notLikedDto);
    }

    @Test
    @DisplayName("ES가 반환한 순서대로 피드를 정렬해 반환한다")
    void ES가_반환한_순서대로_피드를_정렬해_반환한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UserSummary summary = new UserSummary(authorId, "유저", "img.png");

      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      UUID id3 = UUID.randomUUID();
      Feed feed1 = Feed.create(authorId, UUID.randomUUID(), "피드1", DUMMY_SNAPSHOT, List.of());
      Feed feed2 = Feed.create(authorId, UUID.randomUUID(), "피드2", DUMMY_SNAPSHOT, List.of());
      Feed feed3 = Feed.create(authorId, UUID.randomUUID(), "피드3", DUMMY_SNAPSHOT, List.of());
      setFeedId(feed1, id1);
      setFeedId(feed2, id2);
      setFeedId(feed3, id3);

      FeedListParams params = params(10);

      // ES는 3 → 1 → 2 순으로 반환
      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(id3, id1, id2), 3L, null, null, false);
      // DB는 순서를 보장하지 않으므로 다른 순서로 반환
      given(feedRepository.findAllActiveByIds(List.of(id3, id1, id2)))
          .willReturn(List.of(feed1, feed2, feed3));

      given(userSummaryQueryRepository.findByUserIds(anyList())).willReturn(List.of(summary));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto dto1 = new FeedDto(id1, null, null, summary, null, null, "피드1", 0L, 0, false);
      FeedDto dto2 = new FeedDto(id2, null, null, summary, null, null, "피드2", 0L, 0, false);
      FeedDto dto3 = new FeedDto(id3, null, null, summary, null, null, "피드3", 0L, 0, false);
      given(feedMapper.toDto(feed1, summary, false)).willReturn(dto1);
      given(feedMapper.toDto(feed2, summary, false)).willReturn(dto2);
      given(feedMapper.toDto(feed3, summary, false)).willReturn(dto3);

      // when
      CursorPageResponse<FeedDto> result =
          feedPageAssembler.assemble(searchResult, params, currentUserId);

      // then
      assertThat(result.data()).containsExactly(dto3, dto1, dto2);
      assertThat(result.totalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("소프트 삭제된 피드는 목록에서 제외한다")
    void 소프트_삭제된_피드는_목록에서_제외한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      UUID authorId = UUID.randomUUID();
      UserSummary summary = new UserSummary(authorId, "유저", "img.png");

      UUID activeId = UUID.randomUUID();
      UUID deletedId = UUID.randomUUID();
      Feed activeFeed = Feed.create(authorId, UUID.randomUUID(), "살아있는 피드",
          DUMMY_SNAPSHOT, List.of());
      setFeedId(activeFeed, activeId);

      FeedListParams params = params(10);

      FeedSearchResult searchResult =
          new FeedSearchResult(List.of(activeId, deletedId), 2L, null, null, false);
      given(feedRepository.findAllActiveByIds(List.of(activeId, deletedId)))
          .willReturn(List.of(activeFeed));

      given(userSummaryQueryRepository.findByUserIds(anyList())).willReturn(List.of(summary));
      given(feedLikeRepository.findLikedFeedIds(any(), anyList())).willReturn(List.of());

      FeedDto activeDto = new FeedDto(activeId, null, null, summary, null, null,
          "살아있는 피드", 0L, 0, false);
      given(feedMapper.toDto(activeFeed, summary, false)).willReturn(activeDto);

      // when
      CursorPageResponse<FeedDto> result =
          feedPageAssembler.assemble(searchResult, params, currentUserId);

      // then
      assertThat(result.data()).containsExactly(activeDto);
    }
  }
}
