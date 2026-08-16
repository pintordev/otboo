package com.sprint.mission.otboo.security.token.properties;

import com.sprint.mission.otboo.security.token.properties.enums.TokenImplType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.security.token")
public record TokenProperties(

    @NotNull(message = "accessTokenExpiration은 필수 값입니다.")
    @DurationMin(
        minutes = 15,
        message = "accessTokenExpiration은 최소 15분 이상이어야 합니다."
    )
    @DurationMax(
        hours = 1,
        message = "accessTokenExpiration은 보안상 1시간을 초과할 수 없습니다."
    )
    Duration accessTokenExpiration,

    @NotNull(message = "refreshTokenExpiration은 필수 값입니다.")
    @DurationMin(
        days = 1,
        message = "refreshTokenExpiration은 최소 1일 이상이어야 합니다."
    )
    @DurationMax(
        days = 30,
        message = "refreshTokenExpiration은 30일을 초과할 수 없습니다."
    )
    Duration refreshTokenExpiration,

    @NotBlank(message = "accessSecret은 비어있을 수 없습니다.")
    @Size(
        min = 44,
        message = "accessSecret은 최소 32바이트(HS256 최소 키 길이)를 base64로 인코딩한 값이어야 합니다."
    )
    String accessSecret,

    @NotBlank(message = "refreshSecret은 비어있을 수 없습니다.")
    @Size(
        min = 44,
        message = "refreshSecret은 최소 32바이트(HS256 최소 키 길이)를 base64로 인코딩한 값이어야 합니다."
    )
    String refreshSecret,

    TokenImplType impl
) {

  public TokenProperties {
    if (impl == null) {
      impl = TokenImplType.NIMBUS;
    }
  }
}
