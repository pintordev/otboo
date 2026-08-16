package com.sprint.mission.otboo.global.file.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.file")
public record FileProperties(
    FileImplType impl,
    @NotBlank String publicBaseUrl,
    @Positive long maxSizeBytes,
    @NotEmpty Set<String> allowedExtensions,
    @Valid Local local,
    @Valid S3 s3
) {

  public FileProperties {
    if (impl == null) {
      impl = FileImplType.LOCAL;
    }
  }

  @AssertTrue(message = "impl이 local이면 local.upload-dir이, s3면 s3.bucket과 s3.region이 필요합니다.")
  private boolean isModeConfigValid() {
    if (impl == FileImplType.LOCAL) {
      return local != null && StringUtils.hasText(local.uploadDir());
    }
    if (impl == FileImplType.S3) {
      return s3 != null && StringUtils.hasText(s3.bucket()) && StringUtils.hasText(s3.region());
    }
    return true;
  }

  public record Local(@NotBlank String uploadDir) {

  }

  public record S3(@NotBlank String bucket, @NotBlank String region) {

  }
}
