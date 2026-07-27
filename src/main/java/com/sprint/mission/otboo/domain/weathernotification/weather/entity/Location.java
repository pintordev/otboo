package com.sprint.mission.otboo.domain.weathernotification.weather.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

@Entity
@Table(name = "locations")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "latitude", nullable = false)
  private double latitude;

  @Column(name = "longitude", nullable = false)
  private double longitude;

  @Column(name = "x", nullable = false)
  private int x;

  @Column(name = "y", nullable = false)
  private int y;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "location_names")
  private List<String> locationNames;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private Location(double latitude, double longitude, int x, int y, List<String> locationNames) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.x = x;
    this.y = y;
    this.locationNames = locationNames;
  }

  public static Location create(double latitude, double longitude, int x, int y,
      List<String> locationNames) {
    return new Location(latitude, longitude, x, y, locationNames);
  }
}