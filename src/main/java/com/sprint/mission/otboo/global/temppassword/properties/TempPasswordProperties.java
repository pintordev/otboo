package com.sprint.mission.otboo.global.temppassword.properties;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.temp-password")
public record TempPasswordProperties(

    @NotNull(message = "expiration은 필수 값입니다.")
    @DurationMin(minutes = 3, message = "expiration은 최소 3분 이상이어야 합니다.")
    Duration expiration,

    TempPasswordGeneratorType generator,

    TempPasswordRegistryType impl
) {

  public TempPasswordProperties {
    if (generator == null) {
      generator = TempPasswordGeneratorType.RANDOM;
    }
    if (impl == null) {
      impl = TempPasswordRegistryType.REDIS;
    }
  }
}
