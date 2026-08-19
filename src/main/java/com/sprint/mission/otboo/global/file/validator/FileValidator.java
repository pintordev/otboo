package com.sprint.mission.otboo.global.file.validator;

import com.sprint.mission.otboo.global.file.exception.EmptyFileException;
import com.sprint.mission.otboo.global.file.exception.FileTooLargeException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.util.FileExtensionUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class FileValidator {

  private static final Map<String, Set<String>> ALLOWED_MIME_TYPES_BY_EXTENSION = Map.of(
      "jpg", Set.of("image/jpeg"),
      "jpeg", Set.of("image/jpeg"),
      "png", Set.of("image/png"),
      "webp", Set.of("image/webp")
  );

  private final FileProperties fileProperties;
  private final Tika tika = new Tika();

  public void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw EmptyFileException.withNone();
    }

    if (file.getSize() > fileProperties.maxSizeBytes()) {
      throw FileTooLargeException.withSize(file.getSize(), fileProperties.maxSizeBytes());
    }

    String extension = FileExtensionUtils.extract(file.getOriginalFilename())
        .orElseThrow(() -> InvalidFileTypeException.withExtension("unknown"));

    if (!fileProperties.allowedExtensions().contains(extension)) {
      throw InvalidFileTypeException.withExtension(extension);
    }

    validateContentMatchesExtension(file, extension);
  }

  private void validateContentMatchesExtension(MultipartFile file, String extension) {
    String detectedMimeType;
    try (InputStream input = file.getInputStream()) {
      detectedMimeType = tika.detect(input);
    } catch (IOException e) {
      throw InvalidFileTypeException.withExtension(extension);
    }

    Set<String> allowedMimeTypes = ALLOWED_MIME_TYPES_BY_EXTENSION.getOrDefault(extension, Set.of());
    if (!allowedMimeTypes.contains(detectedMimeType)) {
      throw InvalidFileTypeException.withExtension(extension);
    }
  }
}
