package com.sprint.mission.otboo.security.usersession.properties;

import com.sprint.mission.otboo.security.usersession.properties.enums.ConcurrentPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.ExpirationPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.UserSessionRegistryType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.security.user-session")
public record UserSessionProperties(

    @NotNull
    @DurationMin(seconds = 1)
    Duration userSessionExpiration,

    @Positive
    Integer maxDevices,

    ExpirationPolicyType expirationPolicy,

    ConcurrentPolicyType concurrentPolicy,

    UserSessionRegistryType impl
) {

  public UserSessionProperties {
    if (expirationPolicy == null) {
      expirationPolicy = ExpirationPolicyType.ABSOLUTE;
    }
    if (concurrentPolicy == null) {
      concurrentPolicy = ConcurrentPolicyType.MULTI;
    }
    if (impl == null) {
      impl = UserSessionRegistryType.REDIS;
    }
  }

  @AssertTrue(message = "concurrentPolicy가 max이면 maxDevices가 필수입니다.")
  private boolean isMaxDevicesValid() {
    if (concurrentPolicy != ConcurrentPolicyType.MAX) {
      return true;
    }
    return maxDevices != null && maxDevices > 0;
  }
}
