package com.sprint.mission.otboo.global.file.config;

import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(FileProperties.class)
public class FileWebConfig implements WebMvcConfigurer {

  private final FileProperties fileProperties;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (fileProperties.impl() != FileImplType.LOCAL) {
      return;
    }

    Path uploadPath = Path.of(fileProperties.local().uploadDir()).toAbsolutePath().normalize();
    String location = "file:///" + uploadPath.toString().replace("\\", "/") + "/";

    registry.addResourceHandler("/uploads/**")
        .addResourceLocations(location);
  }
}
