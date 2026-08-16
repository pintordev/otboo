package com.sprint.mission.otboo.batch.weatherfetch.config;

import com.sprint.mission.otboo.global.batch.BatchConstants;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.weather-change")
public record WeatherChangeProperties(
    @Positive double temperatureThreshold,
    @Positive @DecimalMax("100.0") double precipitationProbabilityThreshold,
    @Positive double precipitationAmountThreshold,
    // detectAndNotify()가 updatedGrids를 이 크기로 나눠 D0/D1 배치 쿼리의 IN절 크기와
    // 메모리에 한 번에 올라오는 슬롯/baseline을 제한한다(#163, PR #131 리뷰 - 격자 수에
    // 선형인 감지 파이프라인 지적) - weatherRetentionStep과 같은 상한(MAX_CHUNK_SIZE) 적용
    @Positive @Max(BatchConstants.MAX_CHUNK_SIZE) int gridChunkSize
) {

}