package com.sprint.mission.otboo.global.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.file.storage.FileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.LocalFileStorageService;
import com.sprint.mission.otboo.global.file.storage.impl.S3FileStorageService;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

@DisplayName("FileStorageConfig")
class FileStorageConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(FileStorageConfig.class, FileValidator.class)
      .withPropertyValues(
          "otboo.file.public-base-url=http://localhost:8080/uploads",
          "otboo.file.max-size-bytes=5242880",
          "otboo.file.allowed-extensions=jpg,png",
          "otboo.file.local.upload-dir=./uploads");

  @Nested
  @DisplayName("impl 값에 따른 빈 선택")
  class BeanSelection {

    @Test
    @DisplayName("impl이 local이면 LocalFileStorageService 빈만 등록된다")
    void impl이_local이면_LocalFileStorageService_빈만_등록된다() {
      // when & then
      contextRunner.withPropertyValues("otboo.file.impl=local")
          .run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class))
                .isInstanceOf(LocalFileStorageService.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
          });
    }

    @Test
    @DisplayName("impl이 s3면 S3FileStorageService와 S3Client 빈이 등록된다")
    void impl이_s3면_S3FileStorageService와_S3Client_빈이_등록된다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=s3",
              "otboo.file.s3.bucket=otboo-image",
              "otboo.file.s3.region=ap-northeast-2")
          .run(context -> {
            assertThat(context).hasSingleBean(FileStorageService.class);
            assertThat(context.getBean(FileStorageService.class))
                .isInstanceOf(S3FileStorageService.class);
            assertThat(context).hasSingleBean(S3Client.class);
          });
    }
  }
}
