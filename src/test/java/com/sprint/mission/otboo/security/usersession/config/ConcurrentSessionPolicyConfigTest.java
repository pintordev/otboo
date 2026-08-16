package com.sprint.mission.otboo.security.usersession.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MaxDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MultiDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SingleDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import com.sprint.mission.otboo.security.usersession.properties.enums.ConcurrentPolicyType;
import com.sprint.mission.otboo.security.usersession.properties.enums.ExpirationPolicyType;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ConcurrentSessionPolicyConfig")
class ConcurrentSessionPolicyConfigTest {

  private static final int MAX_DEVICE = 5;

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(ConcurrentSessionPolicyConfig.class);

  private UserSessionProperties propertiesOf(ConcurrentPolicyType concurrentPolicy,
      Integer maxDevices) {
    return new UserSessionProperties(
        Duration.ofDays(14), maxDevices, ExpirationPolicyType.ABSOLUTE, concurrentPolicy, null);
  }

  @Nested
  @DisplayName("concurrentPolicy가 single일 때")
  class SingleSelection {

    @Test
    @DisplayName("SingleDeviceConcurrentUserSessionPolicy 빈이 등록된다")
    void 성공_concurrentPolicy가_single이면_SingleDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner
          .withBean(UserSessionProperties.class,
              () -> propertiesOf(ConcurrentPolicyType.SINGLE, null))
          .run(context -> {
            // then
            assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
            assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
                .isInstanceOf(SingleDeviceConcurrentUserSessionPolicy.class);
          });
    }
  }

  @Nested
  @DisplayName("concurrentPolicy가 multi일 때")
  class MultiSelection {

    @Test
    @DisplayName("MultiDeviceConcurrentUserSessionPolicy 빈이 등록된다")
    void 성공_concurrentPolicy가_multi이면_MultiDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner
          .withBean(UserSessionProperties.class,
              () -> propertiesOf(ConcurrentPolicyType.MULTI, null))
          .run(context -> {
            // then
            assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
            assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
                .isInstanceOf(MultiDeviceConcurrentUserSessionPolicy.class);
          });
    }
  }

  @Nested
  @DisplayName("concurrentPolicy가 max일 때")
  class MaxSelection {

    @Test
    @DisplayName("MaxDeviceConcurrentUserSessionPolicy 빈이 등록된다")
    void 성공_concurrentPolicy가_max이면_MaxDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner
          .withBean(UserSessionProperties.class,
              () -> propertiesOf(ConcurrentPolicyType.MAX, MAX_DEVICE))
          .run(context -> {
            // then
            assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
            assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
                .isInstanceOf(MaxDeviceConcurrentUserSessionPolicy.class);
          });
    }

    @Test
    @DisplayName("UserSessionProperties의 maxDevices 값이 그대로 주입된다")
    void 성공_UserSessionProperties의_maxDevices값이_그대로_주입된다() {
      // given & when
      contextRunner
          .withBean(UserSessionProperties.class,
              () -> propertiesOf(ConcurrentPolicyType.MAX, MAX_DEVICE))
          .run(context -> {
            // then
            ConcurrentUserSessionPolicy policy = context.getBean(ConcurrentUserSessionPolicy.class);
            int injected = (int) ReflectionTestUtils.getField(policy, "maxDevices");
            assertThat(injected).isEqualTo(MAX_DEVICE);
          });
    }
  }
}
