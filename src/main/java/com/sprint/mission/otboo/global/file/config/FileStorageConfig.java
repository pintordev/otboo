package com.sprint.mission.otboo.global.file.config;

import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.LocalFileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.S3FileStorageService;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(FileProperties.class)
public class FileStorageConfig {

  // otboo.file.impl과 무관하게 항상 노출한다 — S3Client.builder().build()는 생성 시점에
  // 네트워크 호출을 하지 않아 impl=local에서도 안전하고, S3를 직접 쓰는 배치(유실 이미지 정리 등)가
  // 별도 빈 없이 이 하나를 그대로 주입받을 수 있다.
  @Bean
  public S3Client s3Client(FileProperties fileProperties) {
    return S3Client.builder().region(Region.of(fileProperties.s3().region())).build();
  }

  @Bean
  public FileStorageService fileStorageService(
      FileProperties fileProperties, FileValidator fileValidator, S3Client s3Client) {
    return switch (fileProperties.impl()) {
      case LOCAL -> new LocalFileStorageService(fileProperties, fileValidator);
      case S3 -> new S3FileStorageService(s3Client, fileProperties, fileValidator);
    };
  }
}
