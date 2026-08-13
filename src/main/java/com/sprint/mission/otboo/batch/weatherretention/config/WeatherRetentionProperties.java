package com.sprint.mission.otboo.batch.weatherretention.config;

import com.sprint.mission.otboo.global.batch.BatchConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "batch.weather-retention")
public record WeatherRetentionProperties(
    // BatchConstants.MAX_CHUNK_SIZE와 동일한 상한 - chunkSize가 이보다 크면 Repository가
    // 조회 limit만 clamp할 뿐 Batch 커밋(삭제) 단위는 그대로 커져서 상한 없이 무의미해진다
    @Positive @Max(BatchConstants.MAX_CHUNK_SIZE) int chunkSize,
    // 설정 파일이 .gitignore 대상이라 키가 없으면 기본값 7로 뜨고, 1~6처럼 API/급변 트리거가
    // 참조하는 최소 범위(D0-D1)보다 짧은 값은 기동 시점에 검증 실패로 막는다
    @DefaultValue("7") @Min(7) int retentionDays
) {

}