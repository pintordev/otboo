package com.sprint.mission.otboo.batch.orphanedimage.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "batch.orphaned-image-cleanup")
public record OrphanedImageCleanupProperties(
    @NotEmpty List<@NotBlank String> s3Prefixes,
    @Positive int gracePeriodHours,
    // S3 DeleteObjects는 요청당 최대 1,000개까지만 허용한다 — OrphanedImageWriter가 chunk 전체를
    // 한 번의 요청으로 보내므로 그 이상이면 배치가 깨진다.
    @Positive @Max(1000) int chunkSize,
    @DecimalMin("0.0") @DecimalMax("1.0") double maxDeleteRatio,
    @Positive int maxDeleteAbsolute
) {

}