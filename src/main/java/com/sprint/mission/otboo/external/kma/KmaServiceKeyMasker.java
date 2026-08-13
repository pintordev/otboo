package com.sprint.mission.otboo.external.kma;

import java.util.regex.Pattern;

public final class KmaServiceKeyMasker {

  private static final Pattern SERVICE_KEY_PATTERN = Pattern.compile("serviceKey=[^&\\s]+");
  private static final String MASKED = "serviceKey=***";

  private KmaServiceKeyMasker() {
  }

  public static String mask(String text) {
    if (text == null) {
      return null;
    }
    return SERVICE_KEY_PATTERN.matcher(text).replaceAll(MASKED);
  }
}