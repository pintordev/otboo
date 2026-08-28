package com.sprint.mission.otboo.global.metrics.dashboard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "metrics.dashboard")
public record MetricsDashboardProperties(
    @NotBlank String namespace,
    @NotEmpty List<@NotBlank String> whitelistPrefixes
) {

}