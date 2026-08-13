package com.sprint.mission.otboo.domain.social.feed.repository.querydsl.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.authuser.user.entity.User;
import com.sprint.mission.otboo.domain.social.feed.dto.FeedCommentParams;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.social.feed.entity.Comment;
import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import com.sprint.mission.otboo.domain.social.feed.repository.CommentRepository;
import com.sprint.mission.otboo.domain.social.feed.repository.FeedRepository;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("CommentCustomRepository")
class CommentCustomRepositoryImplTest {

  static final WeatherSnapshot DUMMY_SNAPSHOT = new WeatherSnapshot(
      SkyStatus.CLEAR, PrecipitationType.NONE,
      0.0, 0.0, 28.0, 2.0, 16.0, 31.0);
  @Autowired
  private CommentRepository commentRepository;
  @Autowired
  private TestEntityManager testEntityManager;
  @Autowired
  private FeedRepository feedRepository;

  private User persistUser(String name) {
    return testEntityManager.persist(
        User.create(name, UUID.randomUUID() + "@otboo.io", "password"));
  }

  private Feed persistFeed() {
    User author = persistUser("작성자");
    return feedRepository.save(
        Feed.create(author.getId(), UUID.randomUUID(), "테스트 피드", DUMMY_SNAPSHOT, List.of()));
  }

  private Comment saveComment(UUID feedId, String content) {
    User author = persistUser("댓글작성자");
    return commentRepository.save(Comment.create(feedId, author.getId(), content));
  }

  private void setCreatedAt(UUID commentId, Instant createdAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update comments set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", commentId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("findComments")
  class FindComments {

    @Test
    @DisplayName("해당 피드의 댓글을 createdAt 내림차순으로 반환한다")
    void 해당_피드의_댓글을_createdAt_내림차순으로_반환한다() {
      // given
      UUID feedId = persistFeed().getId();
      Comment older = saveComment(feedId, "오래된 댓글");
      Comment newer = saveComment(feedId, "최신 댓글");
      testEntityManager.flush();
      setCreatedAt(older.getId(), Instant.parse("2026-08-05T07:00:00Z"));
      setCreatedAt(newer.getId(), Instant.parse("2026-08-05T08:00:00Z"));
      saveComment(persistFeed().getId(), "다른 피드 댓글");
      testEntityManager.flush();
      testEntityManager.clear();

      FeedCommentParams params = new FeedCommentParams(null, null, 10);

      // when
      CursorPageResponse<Comment> result = commentRepository.findComments(feedId, params);

      // then
      assertThat(result.data()).extracting(Comment::getContent)
          .containsExactly("최신 댓글", "오래된 댓글");
      assertThat(result.totalCount()).isEqualTo(2L);
      assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("limit보다 많으면 hasNext가 true이고 다음 커서로 이어서 조회한다")
    void limit보다_많으면_hasNext가_true이고_다음_커서로_이어서_조회한다() {
      // given
      UUID feedId = persistFeed().getId();
      Comment c1 = saveComment(feedId, "댓글1");
      Comment c2 = saveComment(feedId, "댓글2");
      Comment c3 = saveComment(feedId, "댓글3");
      testEntityManager.flush();
      setCreatedAt(c1.getId(), Instant.parse("2026-08-05T06:00:00Z"));
      setCreatedAt(c2.getId(), Instant.parse("2026-08-05T07:00:00Z"));
      setCreatedAt(c3.getId(), Instant.parse("2026-08-05T08:00:00Z"));
      testEntityManager.flush();
      testEntityManager.clear();

      // when — 첫 페이지 (limit 2)
      FeedCommentParams firstPage = new FeedCommentParams(null, null, 2);
      CursorPageResponse<Comment> first = commentRepository.findComments(feedId, firstPage);

      // then
      assertThat(first.data()).extracting(Comment::getContent)
          .containsExactly("댓글3", "댓글2");
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isNotNull();
      assertThat(first.totalCount()).isEqualTo(3L);

      // when — 다음 페이지
      FeedCommentParams nextPage = new FeedCommentParams(
          first.nextCursor(), first.nextIdAfter(), 2);
      CursorPageResponse<Comment> second = commentRepository.findComments(feedId, nextPage);

      // then
      assertThat(second.data()).extracting(Comment::getContent)
          .containsExactly("댓글1");
      assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("createdAt이 동일하면 id 내림차순으로 정렬하고 커서로 나머지를 조회한다")
    void createdAt이_동일하면_id_내림차순으로_정렬하고_커서로_나머지를_조회한다() {
      // given
      UUID feedId = persistFeed().getId();
      Instant sameTime = Instant.parse("2026-08-05T08:00:00Z");
      Comment a = saveComment(feedId, "A");
      Comment b = saveComment(feedId, "B");
      testEntityManager.flush();
      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.flush();
      testEntityManager.clear();

      // when — 첫 페이지 (limit 1)
      FeedCommentParams firstPage = new FeedCommentParams(null, null, 1);
      CursorPageResponse<Comment> first = commentRepository.findComments(feedId, firstPage);

      // then — 동일 시각이면 id가 큰 쪽이 먼저
      assertThat(first.data()).hasSize(1);
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isNotNull();
      assertThat(first.nextIdAfter()).isNotNull();
      UUID firstId = first.data().get(0).getId();

      // when — 다음 페이지
      FeedCommentParams nextPage = new FeedCommentParams(
          first.nextCursor(), first.nextIdAfter(), 1);
      CursorPageResponse<Comment> second = commentRepository.findComments(feedId, nextPage);

      // then
      assertThat(second.data()).hasSize(1);
      UUID secondId = second.data().get(0).getId();
      assertThat(firstId.toString()).isGreaterThan(secondId.toString());
      assertThat(List.of(firstId, secondId))
          .containsExactlyInAnyOrder(a.getId(), b.getId());
    }
  }
}
