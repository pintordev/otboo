package com.sprint.mission.otboo.domain.weathernotification.weather.entity;

import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "weathers", uniqueConstraints = @UniqueConstraint(
    name = "UQ_weathers_weather_grid_id_forecast_at",
    columnNames = {"weather_grid_id", "forecast_at"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "weather_grid_id", nullable = false)
  private WeatherGrid weatherGrid;

  @Column(name = "forecasted_at", nullable = false)
  private Instant forecastedAt;

  @Column(name = "forecast_at", nullable = false)
  private Instant forecastAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "sky_status", nullable = false)
  private SkyStatus skyStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "precipitation_type", nullable = false)
  private PrecipitationType precipitationType;

  @Column(name = "precipitation_amount", nullable = false)
  private double precipitationAmount;

  @Column(name = "precipitation_probability", nullable = false)
  private double precipitationProbability;

  @Column(name = "humidity_current", nullable = false)
  private double humidityCurrent;

  @Column(name = "humidity_compared")
  private Double humidityCompared;

  @Column(name = "temperature_current", nullable = false)
  private double temperatureCurrent;

  @Column(name = "temperature_compared")
  private Double temperatureCompared;

  @Column(name = "temperature_min", nullable = false)
  private double temperatureMin;

  @Column(name = "temperature_max", nullable = false)
  private double temperatureMax;

  @Column(name = "wind_speed", nullable = false)
  private double windSpeed;

  @Enumerated(EnumType.STRING)
  @Column(name = "wind_as_word", nullable = false)
  private WindStrength windAsWord;

  @Column(name = "baseline_temperature_current", nullable = false)
  private Double baselineTemperatureCurrent;

  @Enumerated(EnumType.STRING)
  @Column(name = "baseline_precipitation_type", nullable = false)
  private PrecipitationType baselinePrecipitationType;

  @Column(name = "baseline_precipitation_probability", nullable = false)
  private Double baselinePrecipitationProbability;

  @Column(name = "baseline_precipitation_amount", nullable = false)
  private Double baselinePrecipitationAmount;

  // 그날(forecastAt 기준 날짜) 모든 슬롯 row에 동일하게 적용되는 일별 요약값 - skyStatus/
  // precipitationType/humidityCurrent(그 슬롯의 실제값)는 급변 감지·추천·챗봇·피드 스냅샷이
  // 그대로 참조하므로 건드리지 않고, 이 3개는 예보 목록 조회 API가 오늘 이후 날짜를 표시할 때만
  // 대신 사용한다.
  @Enumerated(EnumType.STRING)
  @Column(name = "sky_status_worst")
  private SkyStatus skyStatusWorst;

  @Enumerated(EnumType.STRING)
  @Column(name = "precipitation_type_mode")
  private PrecipitationType precipitationTypeMode;

  @Column(name = "humidity_max")
  private Double humidityMax;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Builder(access = AccessLevel.PRIVATE)
  private Weather(WeatherGrid weatherGrid, Instant forecastedAt, Instant forecastAt,
      SkyStatus skyStatus, PrecipitationType precipitationType, double precipitationAmount,
      double precipitationProbability, double humidityCurrent, Double humidityCompared,
      double temperatureCurrent, Double temperatureCompared, double temperatureMin,
      double temperatureMax, double windSpeed, WindStrength windAsWord,
      Double baselineTemperatureCurrent, PrecipitationType baselinePrecipitationType,
      Double baselinePrecipitationProbability, Double baselinePrecipitationAmount,
      SkyStatus skyStatusWorst, PrecipitationType precipitationTypeMode, Double humidityMax) {
    this.weatherGrid = weatherGrid;
    this.forecastedAt = forecastedAt;
    this.forecastAt = forecastAt;
    this.skyStatus = skyStatus;
    this.precipitationType = precipitationType;
    this.precipitationAmount = precipitationAmount;
    this.precipitationProbability = precipitationProbability;
    this.humidityCurrent = humidityCurrent;
    this.humidityCompared = humidityCompared;
    this.temperatureCurrent = temperatureCurrent;
    this.temperatureCompared = temperatureCompared;
    this.temperatureMin = temperatureMin;
    this.temperatureMax = temperatureMax;
    this.windSpeed = windSpeed;
    this.windAsWord = windAsWord;
    this.baselineTemperatureCurrent = baselineTemperatureCurrent;
    this.baselinePrecipitationType = baselinePrecipitationType;
    this.baselinePrecipitationProbability = baselinePrecipitationProbability;
    this.baselinePrecipitationAmount = baselinePrecipitationAmount;
    this.skyStatusWorst = skyStatusWorst;
    this.precipitationTypeMode = precipitationTypeMode;
    this.humidityMax = humidityMax;
  }

  public static Weather create(WeatherGrid weatherGrid, Instant forecastedAt, Instant forecastAt,
      SkyStatus skyStatus, PrecipitationType precipitationType, double precipitationAmount,
      double precipitationProbability, double humidityCurrent, Double humidityCompared,
      double temperatureCurrent, Double temperatureCompared, double temperatureMin,
      double temperatureMax, double windSpeed, WindStrength windAsWord,
      Double baselineTemperatureCurrent, PrecipitationType baselinePrecipitationType,
      Double baselinePrecipitationProbability, Double baselinePrecipitationAmount,
      SkyStatus skyStatusWorst, PrecipitationType precipitationTypeMode, Double humidityMax) {
    return Weather.builder()
        .weatherGrid(weatherGrid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(skyStatus)
        .precipitationType(precipitationType)
        .precipitationAmount(precipitationAmount)
        .precipitationProbability(precipitationProbability)
        .humidityCurrent(humidityCurrent)
        .humidityCompared(humidityCompared)
        .temperatureCurrent(temperatureCurrent)
        .temperatureCompared(temperatureCompared)
        .temperatureMin(temperatureMin)
        .temperatureMax(temperatureMax)
        .windSpeed(windSpeed)
        .windAsWord(windAsWord)
        .baselineTemperatureCurrent(baselineTemperatureCurrent)
        .baselinePrecipitationType(baselinePrecipitationType)
        .baselinePrecipitationProbability(baselinePrecipitationProbability)
        .baselinePrecipitationAmount(baselinePrecipitationAmount)
        .skyStatusWorst(skyStatusWorst)
        .precipitationTypeMode(precipitationTypeMode)
        .humidityMax(humidityMax)
        .build();
  }
}