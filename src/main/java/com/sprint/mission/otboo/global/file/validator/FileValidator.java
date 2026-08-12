package com.sprint.mission.otboo.global.file.validator;

import com.sprint.mission.otboo.global.file.exception.FileTooLargeException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.util.FileExtensionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileValidator {

  private final FileProperties fileProperties;

  public void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw InvalidFileTypeException.withExtension("empty");
    }

    if (file.getSize() > fileProperties.maxSizeBytes()) {
      throw FileTooLargeException.withSize(file.getSize(), fileProperties.maxSizeBytes());
    }

    String extension = FileExtensionUtils.extract(file.getOriginalFilename())
        .orElseThrow(() -> InvalidFileTypeException.withExtension("unknown"));

    if (!fileProperties.allowedExtensions().contains(extension)) {
      throw InvalidFileTypeException.withExtension(extension);
    }
  }
}
