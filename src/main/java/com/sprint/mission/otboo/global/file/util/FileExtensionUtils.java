package com.sprint.mission.otboo.global.file.util;

import java.util.Locale;
import java.util.Optional;
import org.springframework.util.StringUtils;

public final class FileExtensionUtils {

  private FileExtensionUtils() {
  }

  public static Optional<String> extract(String filename) {
    if (!StringUtils.hasText(filename)) {
      return Optional.empty();
    }

    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex < 0 || dotIndex == filename.length() - 1) {
      return Optional.empty();
    }

    return Optional.of(filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT));
  }
}
