package com.sprint.mission.otboo.domain.social.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "feed_likes",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_feed_likes_feed_id_user_id",
        columnNames = {"feed_id", "user_id"}
    )
)
@Entity
public class FeedLike {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "feed_id", nullable = false)
  private UUID feedId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  private FeedLike(UUID feedId, UUID userId) {
    this.feedId = feedId;
    this.userId = userId;
  }

  public static FeedLike create(UUID feedId, UUID userId) {
    return new FeedLike(feedId, userId);
  }
}