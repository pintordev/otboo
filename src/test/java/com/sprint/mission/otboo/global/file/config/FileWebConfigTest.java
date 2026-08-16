package com.sprint.mission.otboo.global.file.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

@DisplayName("FileWebConfig")
class FileWebConfigTest {

  private FileProperties propertiesOf(FileImplType impl) {
    return new FileProperties(impl, "http://localhost:8080/uploads", 5242880, Set.of("jpg"),
        new FileProperties.Local("./uploads"), null);
  }

  @Nested
  @DisplayName("정적 리소스 등록 (addResourceHandlers)")
  class AddResourceHandlers {

    @Test
    @DisplayName("impl이 local이면 uploads 리소스 핸들러를 등록한다")
    void impl이_local이면_uploads_리소스_핸들러를_등록한다() {
      // given
      FileWebConfig fileWebConfig = new FileWebConfig(propertiesOf(FileImplType.LOCAL));
      ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
      given(registry.addResourceHandler("/uploads/**"))
          .willReturn(mock(ResourceHandlerRegistration.class));

      // when
      fileWebConfig.addResourceHandlers(registry);

      // then
      verify(registry).addResourceHandler("/uploads/**");
    }

    @Test
    @DisplayName("impl이 s3면 리소스 핸들러를 등록하지 않는다")
    void impl이_s3면_리소스_핸들러를_등록하지_않는다() {
      // given
      FileWebConfig fileWebConfig = new FileWebConfig(propertiesOf(FileImplType.S3));
      ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);

      // when
      fileWebConfig.addResourceHandlers(registry);

      // then
      verify(registry, never()).addResourceHandler(org.mockito.ArgumentMatchers.any());
    }
  }
}
