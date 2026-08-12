package com.sprint.mission.otboo.domain.weathernotification.notification.entity;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "weather_change_notification_logs", uniqueConstraints = @UniqueConstraint(
    name = "UQ_weather_change_notification_logs_grid_forecast_at",
    columnNames = {"weather_grid_id", "forecast_at"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeatherChangeNotificationLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "weather_grid_id", nullable = false, updatable = false)
  private WeatherGrid weatherGrid;

  @Column(name = "forecast_at", nullable = false, updatable = false)
  private Instant forecastAt;

  @Column(name = "last_notified_forecasted_at", nullable = false)
  private Instant lastNotifiedForecastedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private WeatherChangeNotificationLog(WeatherGrid weatherGrid, Instant forecastAt,
      Instant lastNotifiedForecastedAt) {
    this.weatherGrid = weatherGrid;
    this.forecastAt = forecastAt;
    this.lastNotifiedForecastedAt = lastNotifiedForecastedAt;
  }

  public static WeatherChangeNotificationLog create(WeatherGrid weatherGrid, Instant forecastAt,
      Instant lastNotifiedForecastedAt) {
    return new WeatherChangeNotificationLog(weatherGrid, forecastAt, lastNotifiedForecastedAt);
  }

  public void updateLastNotified(Instant lastNotifiedForecastedAt) {
    this.lastNotifiedForecastedAt = lastNotifiedForecastedAt;
  }
}