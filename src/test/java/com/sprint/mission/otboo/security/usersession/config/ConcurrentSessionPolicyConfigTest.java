package com.sprint.mission.otboo.security.usersession.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.usersession.policy.ConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MaxDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.MultiDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SingleDeviceConcurrentUserSessionPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class ConcurrentSessionPolicyConfigTest {

  private static final int MAX_DEVICE = 5;

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(ConcurrentSessionPolicyConfig.class)
      .withBean(UserSessionProperties.class,
          () -> new UserSessionProperties(Duration.ofDays(14), MAX_DEVICE));

  @Nested
  class SingleSelection {

    @Test
    void 성공_concurrent_policy가_single이면_SingleDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.concurrent-policy=single").run(context -> {
        // then
        assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
        assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
            .isInstanceOf(SingleDeviceConcurrentUserSessionPolicy.class);
      });
    }

    @Test
    void 성공_프로퍼티가_없으면_기본값으로_SingleDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner.run(context -> {
        // then
        assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
        assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
            .isInstanceOf(SingleDeviceConcurrentUserSessionPolicy.class);
      });
    }
  }

  @Nested
  class MultiSelection {

    @Test
    void 성공_concurrent_policy가_multi이면_MultiDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.concurrent-policy=multi").run(context -> {
        // then
        assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
        assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
            .isInstanceOf(MultiDeviceConcurrentUserSessionPolicy.class);
      });
    }
  }

  @Nested
  class MaxSelection {

    @Test
    void 성공_concurrent_policy가_max이면_MaxDeviceConcurrentUserSessionPolicy빈이_등록된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.concurrent-policy=max").run(context -> {
        // then
        assertThat(context).hasSingleBean(ConcurrentUserSessionPolicy.class);
        assertThat(context.getBean(ConcurrentUserSessionPolicy.class))
            .isInstanceOf(MaxDeviceConcurrentUserSessionPolicy.class);
      });
    }

    @Test
    void 성공_UserSessionProperties의_maxDevice값이_그대로_주입된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.concurrent-policy=max").run(context -> {
        // then
        ConcurrentUserSessionPolicy policy = context.getBean(ConcurrentUserSessionPolicy.class);
        int injected = (int) ReflectionTestUtils.getField(policy, "maxDevices");
        assertThat(injected).isEqualTo(MAX_DEVICE);
      });
    }
  }
}
