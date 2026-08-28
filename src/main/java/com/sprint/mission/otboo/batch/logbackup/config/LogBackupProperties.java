package com.sprint.mission.otboo.batch.logbackup.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "batch.log-backup")
public record LogBackupProperties(
    @NotEmpty @Valid List<LogGroupTarget> logGroups,
    @NotBlank String s3Bucket,
    @Positive int chunkSize,
    @Positive int skipLimit,
    @Positive int retryLimit,
    @DefaultValue("3") @Positive int lookbackDays
) {

  public record LogGroupTarget(@NotBlank String name, @NotBlank String streamPrefix) {

  }
}
