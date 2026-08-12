package com.sprint.mission.otboo.global.file.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@DisplayName("FileProperties")
class FilePropertiesTest {

  @Configuration
  @EnableConfigurationProperties(FileProperties.class)
  static class TestConfig {

  }

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
      .withUserConfiguration(TestConfig.class);

  @Nested
  @DisplayName("필수 값 검증")
  class Validation {

    @Test
    @DisplayName("필수 값이 모두 있으면 정상적으로 바인딩된다")
    void 필수_값이_모두_있으면_정상적으로_바인딩된다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=local",
              "otboo.file.public-base-url=http://localhost:8080/uploads",
              "otboo.file.max-size-bytes=5242880",
              "otboo.file.allowed-extensions=jpg,png",
              "otboo.file.local.upload-dir=./uploads")
          .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("impl이 local인데 local.upload-dir이 없으면 컨텍스트 기동에 실패한다")
    void impl이_local인데_local_upload_dir이_없으면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=local",
              "otboo.file.public-base-url=http://localhost:8080/uploads",
              "otboo.file.max-size-bytes=5242880",
              "otboo.file.allowed-extensions=jpg,png")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("impl이 s3인데 s3 bucket과 region이 없으면 컨텍스트 기동에 실패한다")
    void impl이_s3인데_s3_bucket과_region이_없으면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=s3",
              "otboo.file.public-base-url=https://cdn.otboo.us",
              "otboo.file.max-size-bytes=5242880",
              "otboo.file.allowed-extensions=jpg,png")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("impl이 s3이고 s3 bucket과 region이 있으면 정상적으로 바인딩된다")
    void impl이_s3이고_s3_bucket과_region이_있으면_정상적으로_바인딩된다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=s3",
              "otboo.file.public-base-url=https://cdn.otboo.us",
              "otboo.file.max-size-bytes=5242880",
              "otboo.file.allowed-extensions=jpg,png",
              "otboo.file.s3.bucket=otboo-image",
              "otboo.file.s3.region=ap-northeast-2")
          .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("impl 값이 없으면 컨텍스트 기동에 실패한다")
    void impl_값이_없으면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.public-base-url=http://localhost:8080/uploads",
              "otboo.file.max-size-bytes=5242880",
              "otboo.file.allowed-extensions=jpg,png")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("publicBaseUrl 값이 없으면 컨텍스트 기동에 실패한다")
    void publicBaseUrl_값이_없으면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=local",
              "otboo.file.max-size-bytes=5242880",
              "otboo.file.allowed-extensions=jpg,png")
          .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("allowedExtensions 값이 없으면 컨텍스트 기동에 실패한다")
    void allowedExtensions_값이_없으면_컨텍스트_기동에_실패한다() {
      // when & then
      contextRunner.withPropertyValues(
              "otboo.file.impl=local",
              "otboo.file.public-base-url=http://localhost:8080/uploads",
              "otboo.file.max-size-bytes=5242880")
          .run(context -> assertThat(context).hasFailed());
    }
  }
}
