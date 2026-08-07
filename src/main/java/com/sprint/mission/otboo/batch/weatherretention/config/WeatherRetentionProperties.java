package com.sprint.mission.otboo.batch.weatherretention.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "batch.weather-retention")
public record WeatherRetentionProperties(
    // WeatherCustomRepositoryImpl.MAX_LIMIT과 동일한 상한 - chunkSize가 이보다 크면 Repository가
    // 조회 limit만 clamp할 뿐 Batch 커밋(삭제) 단위는 그대로 커져서 상한 없이 무의미해진다
    @Positive @Max(1000) int chunkSize,
    @Positive int retentionDays
) {

}