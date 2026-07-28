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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "weather_grids",
    uniqueConstraints = @UniqueConstraint(name = "UQ_weather_grids_x_y", columnNames = {"x", "y"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherGrid {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "x", nullable = false)
  private int x;

  @Column(name = "y", nullable = false)
  private int y;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  private WeatherGrid(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public static WeatherGrid create(int x, int y) {
    return new WeatherGrid(x, y);
  }
}