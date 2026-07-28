package com.sprint.mission.otboo.external.kma;

public final class KmaGridConverter {

  private static final double EARTH_RADIUS_KM = 6371.00877;
  private static final double GRID_RESOLUTION_KM = 5.0;
  private static final double RE = EARTH_RADIUS_KM / GRID_RESOLUTION_KM;

  private static final double STD_LAT_1_DEG = 30.0;
  private static final double STD_LAT_2_DEG = 60.0;
  private static final double BASE_LON_DEG = 126.0;
  private static final double BASE_LAT_DEG = 38.0;

  private static final int X_OFFSET = 43;
  private static final int Y_OFFSET = 136;

  private static final double MIN_LAT_DEG = 33.0;
  private static final double MAX_LAT_DEG = 38.5;
  private static final double MIN_LON_DEG = 124.5;
  private static final double MAX_LON_DEG = 131.9;

  private KmaGridConverter() {
  }

  public static KmaGridPoint toGrid(double latitude, double longitude) {
    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
      throw new IllegalArgumentException(
          "좌표 값이 유효하지 않습니다: latitude=" + latitude + ", longitude=" + longitude);
    }
    if (latitude < MIN_LAT_DEG || latitude > MAX_LAT_DEG) {
      throw new IllegalArgumentException(
          "한반도 범위를 벗어난 위도입니다: latitude=" + latitude);
    }
    if (longitude < MIN_LON_DEG || longitude > MAX_LON_DEG) {
      throw new IllegalArgumentException(
          "한반도 범위를 벗어난 경도입니다: longitude=" + longitude);
    }

    double degrad = Math.PI / 180.0;
    double slat1 = STD_LAT_1_DEG * degrad;
    double slat2 = STD_LAT_2_DEG * degrad;
    double olon = BASE_LON_DEG * degrad;
    double olat = BASE_LAT_DEG * degrad;

    double sn = conicFactor(slat2) / conicFactor(slat1);
    sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);

    double sf = conicFactor(slat1);
    sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;

    double ro = conicFactor(olat);
    ro = RE * sf / Math.pow(ro, sn);

    double ra = conicFactor(latitude * degrad);
    ra = RE * sf / Math.pow(ra, sn);

    double theta = longitude * degrad - olon;
    if (theta > Math.PI) {
      theta -= 2.0 * Math.PI;
    }
    if (theta < -Math.PI) {
      theta += 2.0 * Math.PI;
    }
    theta *= sn;

    int nx = (int) Math.floor(ra * Math.sin(theta) + X_OFFSET + 0.5);
    int ny = (int) Math.floor(ro - ra * Math.cos(theta) + Y_OFFSET + 0.5);

    return new KmaGridPoint(nx, ny);
  }

  // LCC 투영법 공식에서 반복 등장하는 tan(π/4 + φ/2) 항(정형위도 계수)
  private static double conicFactor(double angleRad) {
    return Math.tan(Math.PI * 0.25 + angleRad * 0.5);
  }

  public record KmaGridPoint(int nx, int ny) {

  }
}