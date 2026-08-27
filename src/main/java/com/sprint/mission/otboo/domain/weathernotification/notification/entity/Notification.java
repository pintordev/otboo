package com.sprint.mission.otboo.domain.weathernotification.notification.entity;

import com.sprint.mission.otboo.global.event.NotificationLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "notifications")
@Entity
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "event_id", updatable = false)
  private UUID eventId;

  @Column(name = "receiver_id", nullable = false, updatable = false)
  private UUID receiverId;

  @Column(name = "title", nullable = false, updatable = false)
  private String title;

  @Column(name = "content", columnDefinition = "text", nullable = false)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false, updatable = false)
  private NotificationLevel level;

  @Column(name = "sse_delivered_at")
  private Instant sseDeliveredAt;

  private Notification(
      UUID eventId, UUID receiverId, String title, String content, NotificationLevel level) {
    this.eventId = eventId;
    this.receiverId = receiverId;
    this.title = title;
    this.content = content;
    this.level = level;
  }

  public static Notification create(
      UUID eventId, UUID receiverId, String title, String content, NotificationLevel level) {
    return new Notification(eventId, receiverId, title, content, level);
  }

  public void markSseDelivered(Instant deliveredAt) {
    this.sseDeliveredAt = deliveredAt;
  }
}