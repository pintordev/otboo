package com.sprint.mission.otboo.domain.social.directmessage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "direct_messages")
@Entity
public class DirectMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "sender_id", nullable = false)
  private UUID senderId;

  @Column(name = "receiver_id", nullable = false)
  private UUID receiverId;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

  private DirectMessage(UUID senderId, UUID receiverId, String content) {
    this.senderId = senderId;
    this.receiverId = receiverId;
    this.content = content;
  }

  public static DirectMessage create(UUID senderId, UUID receiverId, String content) {
    return new DirectMessage(senderId, receiverId, content);
  }
}