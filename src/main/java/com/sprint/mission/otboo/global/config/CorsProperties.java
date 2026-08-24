package com.sprint.mission.otboo.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "otboo.cors")
@Validated
public record CorsProperties(
    @NotEmpty(message = "allowed-origins는 필수 값입니다.")
    List<
        @NotBlank(message = "allowed-origins에는 빈 값을 포함할 수 없습니다.")
        @Pattern(regexp = "^(?!\\*$).+$", message = "allowed-origins에는 와일드카드(*)를 사용할 수 없습니다.")
        String
        > allowedOrigins
) {

}