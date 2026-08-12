package com.sprint.mission.otboo.global.file.util;

import com.sprint.mission.otboo.global.file.properties.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class FileUrlResolver {

  private final FileProperties fileProperties;

  public String resolve(String key) {
    if (key == null) {
      return null;
    }

    String baseUrl = StringUtils.trimTrailingCharacter(fileProperties.publicBaseUrl(), '/');
    String trimmedKey = StringUtils.trimLeadingCharacter(key, '/');
    return baseUrl + "/" + trimmedKey;
  }
}
