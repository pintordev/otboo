package com.sprint.mission.otboo.domain.weathernotification.weather.util;

public final class LocationBlockCalculator {

  private static final double BLOCK_SIZE_METERS = 50.0;
  private static final double METERS_PER_LAT_DEGREE = 111_320.0;
  private static final double REPRESENTATIVE_LATITUDE_DEG = 36.5; // 한반도 대략 중앙 위도
  private static final double METERS_PER_LON_DEGREE =
      METERS_PER_LAT_DEGREE * Math.cos(Math.toRadians(REPRESENTATIVE_LATITUDE_DEG));

  private static final double LAT_STEP_DEG = BLOCK_SIZE_METERS / METERS_PER_LAT_DEGREE;
  private static final double LON_STEP_DEG = BLOCK_SIZE_METERS / METERS_PER_LON_DEGREE;

  private LocationBlockCalculator() {
  }

  public static BlockIndex toBlock(double latitude, double longitude) {
    int latBlock = (int) Math.floor(latitude / LAT_STEP_DEG);
    int lonBlock = (int) Math.floor(longitude / LON_STEP_DEG);
    return new BlockIndex(latBlock, lonBlock);
  }

  public record BlockIndex(int latBlock, int lonBlock) {

  }
}