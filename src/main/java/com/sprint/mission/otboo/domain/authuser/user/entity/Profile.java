package com.sprint.mission.otboo.domain.authuser.user.entity;

import com.sprint.mission.otboo.domain.authuser.user.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {

  private static final int DEFAULT_TEMPERATURE_SENSITIVITY = 3;

  @Id
  private UUID id;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  private LocalDate birthDate;

  private Double latitude;

  private Double longitude;

  @Column(name = "location_x")
  private Integer locationX;

  @Column(name = "location_y")
  private Integer locationY;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private List<String> locationNames;

  @Column(nullable = false)
  private int temperatureSensitivity;

  private String profileImageUrl;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private Profile(User user, int temperatureSensitivity) {
    this.user = user;
    this.temperatureSensitivity = temperatureSensitivity;
  }

  public static Profile createDefault(User user) {
    return new Profile(user, DEFAULT_TEMPERATURE_SENSITIVITY);
  }
}
