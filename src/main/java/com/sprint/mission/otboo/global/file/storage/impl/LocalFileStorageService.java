package com.sprint.mission.otboo.global.file.storage.impl;

import com.sprint.mission.otboo.global.file.exception.FileStorageException;
import com.sprint.mission.otboo.global.file.exception.InvalidFilePathException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.file.util.FileExtensionUtils;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public class LocalFileStorageService implements FileStorageService {

  private final FileProperties fileProperties;
  private final FileValidator fileValidator;

  public LocalFileStorageService(FileProperties fileProperties, FileValidator fileValidator) {
    this.fileProperties = fileProperties;
    this.fileValidator = fileValidator;
  }

  @Override
  public String store(MultipartFile file, String domain) {

    fileValidator.validate(file);

    String extension = FileExtensionUtils.extract(file.getOriginalFilename())
        .orElseThrow(() -> InvalidFileTypeException.withExtension("unknown"));

    String key = domain + "/" + UUID.randomUUID() + "." + extension;

    Path targetDir = resolveSafePath(fileProperties.local().uploadDir(), domain);
    Path target = resolveSafePath(fileProperties.local().uploadDir(), key);

    try {
      Files.createDirectories(targetDir);
      file.transferTo(target);
    } catch (IOException e) {
      log.error("파일 저장 실패: domain={}, key={}", domain, key, e);
      throw FileStorageException.withCause(e);
    }

    return key;
  }

  @Override
  public void delete(String key) {

    if (!StringUtils.hasText(key)) {
      return;
    }

    Path target;
    try {
      target = resolveSafePath(fileProperties.local().uploadDir(), key);
    } catch (InvalidFilePathException e) {
      log.warn("삭제 요청에 비정상 경로가 포함되어 무시함: {}", key);
      return;
    }

    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      log.warn("파일 삭제 실패(무시하고 진행): {}", key, e);
    }
  }

  private Path resolveSafePath(String baseDir, String... parts) {
    Path base = Path.of(baseDir).toAbsolutePath().normalize();
    Path target = base;
    for (String part : parts) {
      target = target.resolve(part);
    }
    target = target.normalize();

    if (!target.startsWith(base)) {
      throw InvalidFilePathException.withNone();
    }

    return target;
  }
}
