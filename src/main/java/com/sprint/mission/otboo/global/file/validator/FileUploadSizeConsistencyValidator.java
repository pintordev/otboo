package com.sprint.mission.otboo.global.file.validator;

import com.sprint.mission.otboo.global.file.properties.FileProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileUploadSizeConsistencyValidator {

  private final FileProperties fileProperties;
  private final MultipartProperties multipartProperties;

  @PostConstruct
  public void validate() {
    if (multipartProperties.getMaxFileSize().toBytes() < fileProperties.maxSizeBytes()) {
      throw new IllegalStateException(
          "spring.servlet.multipart.max-file-size는 otboo.file.max-size-bytes보다 작을 수 없습니다.");
    }
  }
}
