package com.sprint.mission.otboo.domain.weathernotification.weather.entity;

import com.sprint.mission.otboo.domain.weathernotification.weather.service.WeatherChangeSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
@Table(name = "weather_d1_baselines", uniqueConstraints = @UniqueConstraint(
    name = "UQ_weather_d1_baselines_weather_grid_id_target_date",
    columnNames = {"weather_grid_id", "target_date"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherD1Baseline {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "weather_grid_id", nullable = false, updatable = false)
  private WeatherGrid weatherGrid;

  @Column(name = "target_date", nullable = false, updatable = false)
  private LocalDate targetDate;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "hourly_snapshot", columnDefinition = "jsonb", nullable = false)
  private Map<Instant, WeatherChangeSnapshot> hourlySnapshot;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private WeatherD1Baseline(WeatherGrid weatherGrid, LocalDate targetDate,
      Map<Instant, WeatherChangeSnapshot> hourlySnapshot, Instant capturedAt) {
    this.weatherGrid = weatherGrid;
    this.targetDate = targetDate;
    this.hourlySnapshot = hourlySnapshot;
    this.capturedAt = capturedAt;
  }

  public static WeatherD1Baseline create(WeatherGrid weatherGrid, LocalDate targetDate,
      Map<Instant, WeatherChangeSnapshot> hourlySnapshot, Instant capturedAt) {
    return new WeatherD1Baseline(weatherGrid, targetDate, hourlySnapshot, capturedAt);
  }

  public void updateHourlySnapshot(Map<Instant, WeatherChangeSnapshot> hourlySnapshot,
      Instant capturedAt) {
    this.hourlySnapshot = hourlySnapshot;
    this.capturedAt = capturedAt;
  }
}