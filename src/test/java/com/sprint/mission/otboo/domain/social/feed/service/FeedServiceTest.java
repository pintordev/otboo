package com.sprint.mission.otboo.domain.social.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedListParams;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedSortBy;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedForbiddenException;
import com.sprint.mission.otboo.domain.social.feed.mapper.FeedMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedService")
class FeedServiceTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  FeedService feedService;

  @Mock
  FeedRepository feedRepository;

  @Mock
  FeedMapper feedMapper;

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
            assertThat(fe.getDetails())
                .containsEntry("currentUserId", currentUserId)
                .containsKey("requestedAuthorId");
          });
    }

    @Test
    @DisplayName("정상 요청이면 피드를 저장하고 FeedDto를 반환한다")
    void 정상_요청이면_피드를_저장하고_FeedDto를_반환한다() {
      // given
      UUID currentUserId = UUID.randomUUID();
      FeedCreateRequest request = fm.giveMeBuilder(FeedCreateRequest.class)
          .set("authorId", currentUserId)
          .sample();

      FeedDto expected = new FeedDto(
          UUID.randomUUID(), null, null, request.content(), 0L, 0, false);
      when(feedRepository.save(any(Feed.class))).thenAnswer(inv -> inv.getArgument(0));
      when(feedMapper.toDto(any(Feed.class), any(Boolean.class))).thenReturn(expected);

      // when
      FeedDto result = feedService.create(request, currentUserId);

      // then
      ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
      verify(feedRepository).save(captor.capture());
      Feed saved = captor.getValue();
      assertThat(saved.getAuthorId()).isEqualTo(currentUserId);
      assertThat(saved.getWeatherId()).isEqualTo(request.weatherId());
      assertThat(saved.getContent()).isEqualTo(request.content());
      assertThat(saved.getLikeCount()).isZero();
      assertThat(saved.getCommentCount()).isZero();
      assertThat(result).isEqualTo(expected);
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
          null, null
      );

      Feed feed1 = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "피드1");
      Feed feed2 = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "피드2");

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(feed1, feed2), "커서값", feed2.getId(), true, 2L,
          "createdAt", SortDirection.DESCENDING);
      when(feedRepository.findFeeds(params)).thenReturn(repoPage);

      FeedDto dto1 = new FeedDto(feed1.getId(), null, null, "피드1", 0L, 0, false);
      FeedDto dto2 = new FeedDto(feed2.getId(), null, null, "피드2", 0L, 0, false);
      when(feedMapper.toDto(feed1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result = feedService.getFeeds(params);

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
          null, null
      );

      Feed feed1 = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "피드1");
      Feed feed2 = Feed.create(UUID.randomUUID(), UUID.randomUUID(), "피드2");

      CursorPageResponse<Feed> repoPage = new CursorPageResponse<>(
          List.of(feed1, feed2), null, null, false, 2L,
          "createdAt", SortDirection.DESCENDING);
      when(feedRepository.findFeeds(params)).thenReturn(repoPage);

      FeedDto dto1 = new FeedDto(feed1.getId(), null, null, "피드1", 0L, 0, false);
      FeedDto dto2 = new FeedDto(feed2.getId(), null, null, "피드2", 0L, 0, false);
      when(feedMapper.toDto(feed1, false)).thenReturn(dto1);
      when(feedMapper.toDto(feed2, false)).thenReturn(dto2);

      // when
      CursorPageResponse<FeedDto> result = feedService.getFeeds(params);

      // then
      assertThat(result.hasNext()).isFalse();
      assertThat(result.nextCursor()).isNull();
      assertThat(result.nextIdAfter()).isNull();
      assertThat(result.data()).containsExactly(dto1, dto2);
    }
  }
}