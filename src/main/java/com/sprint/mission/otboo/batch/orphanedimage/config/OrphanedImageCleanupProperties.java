package com.sprint.mission.otboo.batch.orphanedimage.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    @Positive int chunkSize,
    @DecimalMin("0.0") @DecimalMax("1.0") double maxDeleteRatio,
    @Positive int maxDeleteAbsolute
) {

}