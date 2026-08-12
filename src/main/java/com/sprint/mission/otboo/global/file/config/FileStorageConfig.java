package com.sprint.mission.otboo.global.file.config;

import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.LocalFileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.S3FileStorageService;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileStorageConfig {

  @Bean
  @ConditionalOnProperty(name = "otboo.file.impl", havingValue = "local", matchIfMissing = true)
  public FileStorageService localFileStorageService(
      FileProperties fileProperties, FileValidator fileValidator) {
    return new LocalFileStorageService(fileProperties, fileValidator);
  }

  @Bean
  @ConditionalOnProperty(name = "otboo.file.impl", havingValue = "s3")
  public S3Client s3Client(FileProperties fileProperties) {
    return S3Client.builder()
        .region(Region.of(fileProperties.s3().region()))
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "otboo.file.impl", havingValue = "s3")
  public FileStorageService s3FileStorageService(
      S3Client s3Client, FileProperties fileProperties, FileValidator fileValidator) {
    return new S3FileStorageService(s3Client, fileProperties, fileValidator);
  }
}
