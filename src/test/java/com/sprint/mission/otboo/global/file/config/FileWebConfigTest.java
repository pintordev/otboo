package com.sprint.mission.otboo.global.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("FileWebConfig")
class FileWebConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(FileWebConfig.class)
      .withPropertyValues(
          "otboo.file.public-base-url=http://localhost:8080/uploads",
          "otboo.file.max-size-bytes=5242880",
          "otboo.file.allowed-extensions=jpg,png",
          "otboo.file.local.upload-dir=./uploads");

  @Nested
  @DisplayName("impl 값에 따른 빈 등록")
  class BeanRegistration {

    @Test
    @DisplayName("impl이 local이면 FileWebConfig 빈이 등록된다")
    void impl이_local이면_FileWebConfig_빈이_등록된다() {
      // when & then
      contextRunner.withPropertyValues("otboo.file.impl=local")
          .run(context -> assertThat(context).hasSingleBean(FileWebConfig.class));
    }

    @Test
    @DisplayName("impl이 s3면 FileWebConfig 빈이 등록되지 않는다")
    void impl이_s3면_FileWebConfig_빈이_등록되지_않는다() {
      // when & then
      contextRunner.withPropertyValues("otboo.file.impl=s3")
          .run(context -> assertThat(context).doesNotHaveBean(FileWebConfig.class));
    }
  }
}
