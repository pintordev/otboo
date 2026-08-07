package com.sprint.mission.otboo.domain.social.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.social.common.dto.UserSummary;
import com.sprint.mission.otboo.domain.social.common.repository.querydsl.UserSummaryQueryRepository;
import com.sprint.mission.otboo.domain.social.feed.dto.CommentCreateRequest;
import com.sprint.mission.otboo.domain.social.feed.dto.CommentDto;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCommentParams;
import com.sprint.mission.otboo.domain.social.feed.entity.Comment;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedForbiddenException;
import com.sprint.mission.otboo.domain.social.feed.exception.FeedNotFoundException;
import com.sprint.mission.otboo.domain.social.feed.mapper.CommentMapper;
import com.sprint.mission.otboo.domain.social.feed.repository.CommentRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService")
class CommentServiceTest {

  static final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @Mock
  CommentRepository commentRepository;
  @Mock
  FeedRepository feedRepository;
  @Mock
  CommentMapper commentMapper;
  @Mock
  UserSummaryQueryRepository userSummaryQueryRepository;
  @Mock
  ApplicationEventPublisher eventPublisher;

  @InjectMocks
  CommentService commentService;

  @Nested
  @DisplayName("댓글 등록")
  class Create {

    @Test
    @DisplayName("댓글을 저장하고 CommentDto를 반환한다")
    void 댓글을_저장하고_CommentDto를_반환한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", userId)
          .set("content", "댓글 내용")
          .sample();

      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      Comment saved = Comment.create(feedId, userId, "댓글 내용");
      given(commentRepository.save(any(Comment.class))).willReturn(saved);
      given(feedRepository.incrementCommentCount(feedId)).willReturn(1);
      UserSummary author = new UserSummary(userId, "경신", null);
      given(userSummaryQueryRepository.findByUserId(userId)).willReturn(author);
      given(feedRepository.findAuthorId(feedId)).willReturn(Optional.of(UUID.randomUUID()));

      CommentDto expected = new CommentDto(UUID.randomUUID(), null, feedId, author, "댓글 내용");
      given(commentMapper.toDto(saved, author)).willReturn(expected);

      // when
      CommentDto result = commentService.create(feedId, request, userId);

      // then
      assertThat(result).isEqualTo(expected);
      verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("댓글을 저장하면 피드의 댓글 카운트를 증가시킨다")
    void 댓글을_저장하면_피드의_댓글_카운트를_증가시킨다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", userId)
          .set("content", "댓글 내용")
          .sample();

      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      Comment saved = Comment.create(feedId, userId, "댓글 내용");
      given(commentRepository.save(any(Comment.class))).willReturn(saved);
      given(feedRepository.incrementCommentCount(feedId)).willReturn(1);
      given(userSummaryQueryRepository.findByUserId(userId))
          .willReturn(new UserSummary(userId, "경신", null));
      given(feedRepository.findAuthorId(feedId)).willReturn(Optional.of(UUID.randomUUID()));

      // when
      commentService.create(feedId, request, userId);

      // then
      verify(feedRepository).incrementCommentCount(feedId);
    }

    @Test
    @DisplayName("반환하는 CommentDto에 작성자 정보를 채운다")
    void 반환하는_CommentDto에_작성자_정보를_채운다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", userId)
          .set("content", "댓글 내용")
          .sample();

      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      Comment saved = Comment.create(feedId, userId, "댓글 내용");
      given(commentRepository.save(any(Comment.class))).willReturn(saved);
      given(feedRepository.incrementCommentCount(feedId)).willReturn(1);
      UserSummary author = new UserSummary(userId, "경신", null);
      given(userSummaryQueryRepository.findByUserId(userId)).willReturn(author);
      given(feedRepository.findAuthorId(feedId)).willReturn(Optional.of(UUID.randomUUID()));

      CommentDto expected = new CommentDto(UUID.randomUUID(), null, feedId, author, "댓글 내용");
      given(commentMapper.toDto(saved, author)).willReturn(expected);

      // when
      CommentDto result = commentService.create(feedId, request, userId);

      // then
      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("요청 authorId가 인증 사용자와 다르면 FeedForbiddenException을 던진다")
    void 요청_authorId가_인증_사용자와_다르면_FeedForbiddenException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      UUID otherAuthorId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", otherAuthorId)
          .set("content", "댓글 내용")
          .sample();

      // when & then
      assertThatThrownBy(() -> commentService.create(feedId, request, currentUserId))
          .isInstanceOf(FeedForbiddenException.class);
    }

    @Test
    @DisplayName("피드가 존재하지 않으면 FeedNotFoundException을 던진다")
    void 피드가_존재하지_않으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", userId)
          .set("content", "댓글 내용")
          .sample();
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> commentService.create(feedId, request, userId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("카운트 증가 대상 피드가 없으면 FeedNotFoundException을 던진다")
    void 카운트_증가_대상_피드가_없으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", userId)
          .set("content", "댓글 내용")
          .sample();

      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      given(commentRepository.save(any(Comment.class)))
          .willReturn(Comment.create(feedId, userId, "댓글 내용"));
      given(feedRepository.incrementCommentCount(feedId)).willReturn(0);

      // when & then
      assertThatThrownBy(() -> commentService.create(feedId, request, userId))
          .isInstanceOf(FeedNotFoundException.class);
    }

    @Test
    @DisplayName("댓글 등록에 성공하면 피드 작성자에게 알림 이벤트를 발행한다")
    void 댓글_등록에_성공하면_피드_작성자에게_알림_이벤트를_발행한다() {
      // given
      UUID feedId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID feedAuthorId = UUID.randomUUID();
      CommentCreateRequest request = fm.giveMeBuilder(CommentCreateRequest.class)
          .set("feedId", feedId)
          .set("authorId", userId)
          .set("content", "댓글 내용")
          .sample();

      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);
      Comment saved = Comment.create(feedId, userId, "댓글 내용");
      given(commentRepository.save(any(Comment.class))).willReturn(saved);
      given(feedRepository.incrementCommentCount(feedId)).willReturn(1);
      given(userSummaryQueryRepository.findByUserId(userId))
          .willReturn(new UserSummary(userId, "경신", null));
      given(feedRepository.findAuthorId(feedId)).willReturn(Optional.of(feedAuthorId));

      // when
      commentService.create(feedId, request, userId);

      // then
      ArgumentCaptor<NotificationRequestedEvent> captor =
          ArgumentCaptor.forClass(NotificationRequestedEvent.class);
      verify(eventPublisher).publishEvent(captor.capture());
      NotificationRequestedEvent event = captor.getValue();
      assertThat(event.receiverIds()).containsExactly(feedAuthorId);
      assertThat(event.title()).contains("경신");
      assertThat(event.content()).isEqualTo("댓글 내용");
    }
  }

  @Nested
  @DisplayName("댓글 목록 조회")
  class GetComments {

    @Test
    @DisplayName("레포가 준 페이지를 CommentDto로 변환하고 작성자를 배치로 채운다")
    void 레포가_준_페이지를_CommentDto로_변환하고_작성자를_배치로_채운다() {
      // given
      UUID feedId = UUID.randomUUID();
      FeedCommentParams params = new FeedCommentParams(null, null, 10);

      UUID authorId = UUID.randomUUID();
      Comment comment = Comment.create(feedId, authorId, "댓글 내용");
      CursorPageResponse<Comment> repoPage = new CursorPageResponse<>(
          List.of(comment), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);
      given(commentRepository.findComments(feedId, params)).willReturn(repoPage);

      UserSummary author = new UserSummary(authorId, "경신", null);
      given(userSummaryQueryRepository.findByUserIds(List.of(authorId)))
          .willReturn(List.of(author));

      CommentDto dto = new CommentDto(comment.getId(), null, feedId, author, "댓글 내용");
      given(commentMapper.toDto(comment, author)).willReturn(dto);
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);

      // when
      CursorPageResponse<CommentDto> result = commentService.getComments(feedId, params);

      // then
      assertThat(result.data()).containsExactly(dto);
      assertThat(result.totalCount()).isEqualTo(1L);
      assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("댓글이 없으면 빈 페이지를 반환하고 작성자 배치 조회를 하지 않는다")
    void 댓글이_없으면_빈_페이지를_반환하고_작성자_배치_조회를_하지_않는다() {
      // given
      UUID feedId = UUID.randomUUID();
      FeedCommentParams params = new FeedCommentParams(null, null, 10);

      CursorPageResponse<Comment> emptyPage = new CursorPageResponse<>(
          List.of(), null, null, false, 0L, "createdAt", SortDirection.DESCENDING);
      given(commentRepository.findComments(feedId, params)).willReturn(emptyPage);
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(true);

      // when
      CursorPageResponse<CommentDto> result = commentService.getComments(feedId, params);

      // then
      assertThat(result.data()).isEmpty();
      assertThat(result.totalCount()).isZero();
      verify(userSummaryQueryRepository, never()).findByUserIds(any());
    }

    @Test
    @DisplayName("피드가 존재하지 않으면 FeedNotFoundException을 던진다")
    void 피드가_존재하지_않으면_FeedNotFoundException을_던진다() {
      // given
      UUID feedId = UUID.randomUUID();
      FeedCommentParams params = new FeedCommentParams(null, null, 10);
      given(feedRepository.existsByIdAndSoftDeletable_DeletedAtIsNull(feedId)).willReturn(false);

      // when & then
      assertThatThrownBy(() -> commentService.getComments(feedId, params))
          .isInstanceOf(FeedNotFoundException.class);
    }
  }
}