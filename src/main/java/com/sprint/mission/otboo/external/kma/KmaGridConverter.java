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

  private KmaGridConverter() {
  }

  public static KmaGridPoint toGrid(double latitude, double longitude) {
    double degrad = Math.PI / 180.0;
    double slat1 = STD_LAT_1_DEG * degrad;
    double slat2 = STD_LAT_2_DEG * degrad;
    double olon = BASE_LON_DEG * degrad;
    double olat = BASE_LAT_DEG * degrad;

    double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
    sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);

    double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
    sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;

    double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
    ro = RE * sf / Math.pow(ro, sn);

    double ra = Math.tan(Math.PI * 0.25 + latitude * degrad * 0.5);
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

  public record KmaGridPoint(int nx, int ny) {

  }
}