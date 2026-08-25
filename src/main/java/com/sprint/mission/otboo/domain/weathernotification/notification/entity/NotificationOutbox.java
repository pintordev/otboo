package com.sprint.mission.otboo.domain.weathernotification.notification.entity;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notification_outboxes")
@Entity
public class NotificationOutbox {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "topic", nullable = false, updatable = false)
  private String topic;

  @Column(name = "payload", columnDefinition = "text", nullable = false, updatable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private NotificationOutboxStatus status;

  @Column(name = "published_at")
  private Instant publishedAt;

  private NotificationOutbox(String topic, String payload) {
    this.topic = topic;
    this.payload = payload;
    this.status = NotificationOutboxStatus.PENDING;
  }

  public static NotificationOutbox create(String topic, String payload) {
    return new NotificationOutbox(topic, payload);
  }

  public void markPublished(Instant publishedAt) {
    this.status = NotificationOutboxStatus.PUBLISHED;
    this.publishedAt = publishedAt;
  }
}