package com.sprint.mission.otboo.domain.social.feed.repository;

import com.sprint.mission.otboo.domain.social.feed.entity.Feed;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Feed f set f.likeCount = f.likeCount + 1 "
      + "where f.id = :feedId and f.softDeletable.deletedAt is null")
  int incrementLikeCount(@Param("feedId") UUID feedId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Feed f set f.likeCount = f.likeCount - 1 "
      + "where f.id = :feedId and f.likeCount > 0 and f.softDeletable.deletedAt is null")
  int decrementLikeCount(@Param("feedId") UUID feedId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("update Feed f set f.commentCount = f.commentCount + 1 "
      + "where f.id = :feedId and f.softDeletable.deletedAt is null")
  int incrementCommentCount(@Param("feedId") UUID feedId);

  boolean existsByIdAndSoftDeletable_DeletedAtIsNull(UUID id);

  @Query("select f.authorId from Feed f where f.id = :feedId and f.softDeletable.deletedAt is null")
  Optional<UUID> findAuthorId(@Param("feedId") UUID feedId);

  @Query("select f from Feed f where f.id in :ids and f.softDeletable.deletedAt is null")
  List<Feed> findAllActiveByIds(@Param("ids") Collection<UUID> ids);

  // (created_at, id) 튜플 비교
  // OR 형태로 쓰면 옵티마이저가 range scan으로 접지 못해 idx_feeds_active_created_at_id를 온전히 타지 못한다.
  // JPQL은 튜플 비교를 지원하지 않아 네이티브 쿼리로 작성한다.
  @Query(value = """
      select * from feeds
      where deleted_at is null
        and (created_at, id) > (:lastCreatedAt, :lastId)
      order by created_at, id
      limit :limit
      """, nativeQuery = true)
  List<Feed> findForReindex(@Param("lastCreatedAt") Instant lastCreatedAt,
      @Param("lastId") UUID lastId, @Param("limit") int limit);

  // (updated_at, id) 튜플 비교. 조건(updated_at >= :since)과 정렬 축을 맞춰야
  // idx_feeds_active_updated_at_id를 온전히 탈 수 있다.
  // 변경분을 훑는 배치이므로 등록순이 아니라 수정순 페이징이 의미상으로도 맞다.
  @Query(value = """
      select * from feeds
      where deleted_at is null
        and updated_at >= :since
        and (updated_at, id) > (:lastUpdatedAt, :lastId)
      order by updated_at, id
      limit :limit
      """, nativeQuery = true)
  List<Feed> findForIncrementalReindex(@Param("since") Instant since,
      @Param("lastUpdatedAt") Instant lastUpdatedAt,
      @Param("lastId") UUID lastId, @Param("limit") int limit);

  // feeds.ootds(jsonb) 배열 안의 각 원소에서 imageUrl만 뽑는다. 소프트 삭제된 피드도 포함 —
  // 삭제된 피드라도 스냅샷 이미지가 남아있으면 여전히 보호 대상이다.
  @Query(value = """
      select distinct elem ->> 'imageUrl'
      from feeds, jsonb_array_elements(ootds) as elem
      where elem ->> 'imageUrl' is not null
      """, nativeQuery = true)
  Set<String> findAllOotdImageKeys();

  @Query("select count(f) from Feed f where f.softDeletable.deletedAt is null")
  long countActive();
}
