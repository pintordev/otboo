package com.sprint.mission.otboo.domain.social.feed.repository;

import com.sprint.mission.otboo.domain.social.feed.entity.FeedLike;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}