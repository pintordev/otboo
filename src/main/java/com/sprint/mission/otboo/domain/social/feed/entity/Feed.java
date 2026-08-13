package com.sprint.mission.otboo.domain.social.feed.entity;

import com.sprint.mission.otboo.domain.social.feed.dto.OotdSnapshot;
import com.sprint.mission.otboo.domain.social.feed.dto.WeatherSnapshot;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.global.entity.SoftDeletable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "feeds")
@Entity
public class Feed {

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

  @Embedded
  private SoftDeletable softDeletable = new SoftDeletable();

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  // 참조가 아니라 기록용 값이다 — FK 없음, weathers 원본은 retention 배치로 물리 삭제된다.
  // 화면 표시는 이 필드가 아니라 아래 skyStatus/precipitationType 등 Feed 자체 스냅샷 컬럼을 쓴다.
  // weatherRepository.findById(weatherId)로 원본을 다시 조회하려 하면 안 됨(삭제돼 없을 수 있음).
  @Column(name = "weather_id")
  private UUID weatherId;

  // 날씨 스냅샷 — 등록 시점 값 복사
  @Enumerated(EnumType.STRING)
  @Column(name = "sky_status")
  private SkyStatus skyStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "precipitation_type")
  private PrecipitationType precipitationType;

  @Column(name = "precipitation_amount")
  private Double precipitationAmount;

  @Column(name = "precipitation_probability")
  private Double precipitationProbability;

  @Column(name = "temperature_current")
  private Double temperatureCurrent;

  @Column(name = "temperature_compared")
  private Double temperatureCompared;

  @Column(name = "temperature_min")
  private Double temperatureMin;

  @Column(name = "temperature_max")
  private Double temperatureMax;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "ootds", columnDefinition = "jsonb")
  private List<OotdSnapshot> ootds;

  @Column(name = "content", columnDefinition = "text", nullable = false)
  private String content;

  @Column(name = "like_count", nullable = false)
  private long likeCount;

  @Column(name = "comment_count", nullable = false)
  private int commentCount;

  private Feed(UUID authorId, UUID weatherId, String content,
      WeatherSnapshot weatherSnapshot, List<OotdSnapshot> ootdSnapshots) {
    this.authorId = authorId;
    this.weatherId = weatherId;
    this.content = content;
    if (weatherSnapshot != null) {
      this.skyStatus = weatherSnapshot.skyStatus();
      this.precipitationType = weatherSnapshot.precipitationType();
      this.precipitationAmount = weatherSnapshot.precipitationAmount();
      this.precipitationProbability = weatherSnapshot.precipitationProbability();
      this.temperatureCurrent = weatherSnapshot.temperatureCurrent();
      this.temperatureCompared = weatherSnapshot.temperatureCompared();
      this.temperatureMin = weatherSnapshot.temperatureMin();
      this.temperatureMax = weatherSnapshot.temperatureMax();
    }
    this.ootds = ootdSnapshots == null ? List.of() : List.copyOf(ootdSnapshots);
    this.likeCount = 0;
    this.commentCount = 0;
  }

  public static Feed create(UUID authorId, UUID weatherId, String content,
      WeatherSnapshot weatherSnapshot, List<OotdSnapshot> ootdSnapshots) {
    return new Feed(authorId, weatherId, content, weatherSnapshot, ootdSnapshots);
  }

  public void updateContent(String content) {
    this.content = content;
  }

  public boolean isDeleted() {
    return softDeletable != null && softDeletable.isDeleted();
  }

  public void delete() {
    if (softDeletable == null) {
      softDeletable = new SoftDeletable();
    }
    softDeletable.delete();
  }
}