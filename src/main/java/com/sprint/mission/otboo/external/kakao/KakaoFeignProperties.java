package com.sprint.mission.otboo.external.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weather.kakao.timeout")
public record KakaoFeignProperties(
    Duration connect,
    Duration read
) {

}