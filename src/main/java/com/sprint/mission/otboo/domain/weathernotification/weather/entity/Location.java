package com.sprint.mission.otboo.domain.weathernotification.weather.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "locations",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_locations_lat_block_lon_block",
        columnNames = {"lat_block", "lon_block"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "lat_block", nullable = false)
  private int latBlock;

  @Column(name = "lon_block", nullable = false)
  private int lonBlock;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "location_names")
  private List<String> locationNames;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  private Location(int latBlock, int lonBlock, List<String> locationNames) {
    this.latBlock = latBlock;
    this.lonBlock = lonBlock;
    this.locationNames = locationNames;
  }

  public static Location create(int latBlock, int lonBlock, List<String> locationNames) {
    return new Location(latBlock, lonBlock, locationNames);
  }
}