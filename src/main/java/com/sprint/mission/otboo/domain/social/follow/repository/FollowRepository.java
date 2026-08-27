package com.sprint.mission.otboo.domain.social.follow.repository;

import com.sprint.mission.otboo.domain.social.follow.entity.Follow;
import com.sprint.mission.otboo.domain.social.follow.repository.querydsl.FollowCustomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, UUID>, FollowCustomRepository {

  boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

  Optional<Follow> findByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

  long countByFolloweeId(UUID followeeId);

  long countByFollowerId(UUID followerId);

  @Query("select f.followerId from Follow f where f.followeeId = :followeeId")
  List<UUID> findFollowerIds(@Param("followeeId") UUID followeeId);

  /**
   * 중복이면 아무것도 하지 않고 0을, 새로 저장하면 1을 반환한다.
   *
   * <p>제약 위반 예외를 만들지 않으므로 트랜잭션이 오염되지 않는다. 네이티브 INSERT라
   * {@code @GeneratedValue}와 {@code @CreatedDate}를 타지 않아 id·created_at을 직접 넘긴다.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      insert into follows (id, follower_id, followee_id, created_at)
      values (:id, :followerId, :followeeId, :createdAt)
      on conflict (follower_id, followee_id) do nothing
      """, nativeQuery = true)
  int insertIgnoreConflict(@Param("id") UUID id,
      @Param("followerId") UUID followerId,
      @Param("followeeId") UUID followeeId,
      @Param("createdAt") Instant createdAt);

}
