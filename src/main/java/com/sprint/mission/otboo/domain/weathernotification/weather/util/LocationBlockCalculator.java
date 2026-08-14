package com.sprint.mission.otboo.domain.weathernotification.weather.util;

import com.sprint.mission.otboo.domain.weathernotification.weather.config.LocationBlockProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LocationBlockCalculator {

  private static final double METERS_PER_LAT_DEGREE = 111_320.0;
  private static final double REPRESENTATIVE_LATITUDE_DEG = 36.5; // 한반도 대략 중앙 위도
  private static final double METERS_PER_LON_DEGREE =
      METERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(REPRESENTATIVE_LATITUDE_DEG));

  private final LocationBlockProperties properties;

  public BlockIndex toBlock(double latitude, double longitude) {
    double latStepDeg = properties.blockSizeMeters() / METERS_PER_LAT_DEGREE;
    double lonStepDeg = properties.blockSizeMeters() / METERS_PER_LON_DEGREE;
    int latBlock = (int) Math.floor(latitude / latStepDeg);
    int lonBlock = (int) Math.floor(longitude / lonStepDeg);
    return new BlockIndex(latBlock, lonBlock);
  }

  public record BlockIndex(int latBlock, int lonBlock) {

  }
}