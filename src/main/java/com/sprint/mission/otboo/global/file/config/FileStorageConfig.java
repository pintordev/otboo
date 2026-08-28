package com.sprint.mission.otboo.global.file.config;

import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.LocalFileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.S3FileStorageService;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileStorageConfig {

  @Bean
  public FileStorageService fileStorageService(
      FileProperties fileProperties, FileValidator fileValidator, S3Client s3Client) {
    return switch (fileProperties.impl()) {
      case LOCAL -> new LocalFileStorageService(fileProperties, fileValidator);
      case S3 -> new S3FileStorageService(s3Client, fileProperties, fileValidator);
    };
  }
}
