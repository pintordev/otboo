package com.sprint.mission.otboo.domain.clothesrecommend.attributedef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "clothes_attribute_defs",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_clothes_attribute_defs_name",
        columnNames = "name"))
@Entity
public class ClothesAttributeDef{

  @Id
  @Column(columnDefinition = "uuid", nullable = false, updatable = false)
  private UUID id = UUID.randomUUID();

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  private Instant updatedAt;

  @Column(nullable = false, unique = true, length = 50)
  private String name;

  public static ClothesAttributeDef create(String name) {
    ClothesAttributeDef def = new ClothesAttributeDef();
    def.name = name;
    return def;
  }

  public void changeName(String newName) {
    this.name = newName;
  }
}