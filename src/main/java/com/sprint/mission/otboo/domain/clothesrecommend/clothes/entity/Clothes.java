package com.sprint.mission.otboo.domain.clothesrecommend.clothes.entity;

import com.sprint.mission.otboo.domain.clothesrecommend.clothes.dto.ClothesType;
import com.sprint.mission.otboo.global.entity.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "clothes")
@Entity
public class Clothes {

  @Id
  @Column(columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id = UUID.randomUUID();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  private Instant updatedAt;

  @Embedded
  private SoftDeletable softDeletable = new SoftDeletable();

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "image_url", length = 255)
  private String imageUrl;

  @Column(name = "purchase_url", length = 500)
  private String purchaseUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ClothesType type;

  public static Clothes create(UUID ownerId, String name, ClothesType type) {
    Clothes clothes = new Clothes();
    clothes.ownerId = ownerId;
    clothes.name = name;
    clothes.type = type;
    return clothes;
  }

  public void changeImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public void changeName(String name) {
    this.name = name;
  }

  public void changeType(ClothesType type) {
    this.type = type;
  }

  public void delete() {
    softDeletable.delete();
  }

  public boolean isDeleted() {
    return softDeletable.isDeleted();
  }
}