package com.sprint.mission.otboo.global.file.storage.impl;

import com.sprint.mission.otboo.global.file.exception.FileStorageException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.file.util.FileExtensionUtils;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
public class S3FileStorageService implements FileStorageService {

  private final S3Client s3Client;
  private final FileProperties fileProperties;
  private final FileValidator fileValidator;

  public S3FileStorageService(
      S3Client s3Client,
      FileProperties fileProperties,
      FileValidator fileValidator) {

    this.s3Client = s3Client;
    this.fileProperties = fileProperties;
    this.fileValidator = fileValidator;
  }

  @Override
  public String store(MultipartFile file, String domain) {

    fileValidator.validate(file);

    String extension = FileExtensionUtils.extract(file.getOriginalFilename())
        .orElseThrow(() -> InvalidFileTypeException.withExtension("unknown"));

    String key = domain + "/" + UUID.randomUUID() + "." + extension;

    try {
      PutObjectRequest request = PutObjectRequest.builder()
          .bucket(fileProperties.s3().bucket())
          .key(key)
          .contentType(file.getContentType())
          .build();

      s3Client.putObject(request,
          RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

    } catch (IOException | SdkException e) {
      log.error("S3 업로드 실패: domain={}, key={}", domain, key, e);
      throw FileStorageException.withCause(e);
    }

    return key;
  }

  @Override
  public void delete(String key) {

    if (!StringUtils.hasText(key)) {
      return;
    }

    try {
      s3Client.deleteObject(DeleteObjectRequest.builder()
          .bucket(fileProperties.s3().bucket())
          .key(key)
          .build());
    } catch (SdkException e) {
      log.warn("S3 파일 삭제 실패(무시하고 진행): {}", key, e);
    }
  }

  @PreDestroy
  public void close() {
    s3Client.close();
  }
}
