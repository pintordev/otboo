package com.sprint.mission.otboo.domain.social.feed.repository;

import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {

  boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

  long deleteByFeedIdAndUserId(UUID feedId, UUID userId);

  @Query("select fl.feedId from FeedLike fl where fl.userId = :userId and fl.feedId in :feedIds")
  List<UUID> findLikedFeedIds(
      @Param("userId") UUID userId,
      @Param("feedIds") Collection<UUID> feedIds
  );

  /**
   * 중복이면 아무것도 하지 않고 0을, 새로 저장하면 1을 반환한다.
   *
   * <p>제약 위반 예외를 만들지 않으므로 트랜잭션이 오염되지 않는다. 네이티브 INSERT라
   * {@code @GeneratedValue}와 {@code @CreatedDate}를 타지 않아 id·created_at을 직접 넘긴다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      insert into feed_likes (id, feed_id, user_id, created_at)
      values (:id, :feedId, :userId, :createdAt)
      on conflict (feed_id, user_id) do nothing
      """, nativeQuery = true)
  int insertIgnoreConflict(@Param("id") UUID id,
      @Param("feedId") UUID feedId,
      @Param("userId") UUID userId,
      @Param("createdAt") Instant createdAt);
}
