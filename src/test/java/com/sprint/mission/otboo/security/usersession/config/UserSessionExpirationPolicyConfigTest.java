package com.sprint.mission.otboo.security.usersession.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.security.usersession.policy.UserSessionExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.AbsoluteExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.policy.impl.SlidingExpirationPolicy;
import com.sprint.mission.otboo.security.usersession.properties.UserSessionProperties;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UserSessionExpirationPolicyConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(UserSessionExpirationPolicyConfig.class)
      .withBean(UserSessionProperties.class, () -> new UserSessionProperties(Duration.ofDays(14), 3));

  @Nested
  class AbsoluteSelection {

    @Test
    void 성공_expiration_policy가_absolute이면_AbsoluteExpirationPolicy빈이_등록된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.expiration-policy=absolute").run(context -> {
        // then
        assertThat(context).hasSingleBean(UserSessionExpirationPolicy.class);
        assertThat(context.getBean(UserSessionExpirationPolicy.class))
            .isInstanceOf(AbsoluteExpirationPolicy.class);
      });
    }

    @Test
    void 성공_프로퍼티가_없으면_기본값으로_AbsoluteExpirationPolicy빈이_등록된다() {
      // given & when
      contextRunner.run(context -> {
        // then
        assertThat(context).hasSingleBean(UserSessionExpirationPolicy.class);
        assertThat(context.getBean(UserSessionExpirationPolicy.class))
            .isInstanceOf(AbsoluteExpirationPolicy.class);
      });
    }
  }

  @Nested
  class SlidingSelection {

    @Test
    void 성공_expiration_policy가_sliding이면_SlidingExpirationPolicy빈이_등록된다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.expiration-policy=sliding").run(context -> {
        // then
        assertThat(context).hasSingleBean(UserSessionExpirationPolicy.class);
        assertThat(context.getBean(UserSessionExpirationPolicy.class))
            .isInstanceOf(SlidingExpirationPolicy.class);
      });
    }
  }

  @Nested
  class InvalidSelection {

    @Test
    void 실패_expiration_policy가_알수없는_값이면_UserSessionExpirationPolicy빈이_등록되지_않는다() {
      // given & when
      contextRunner.withPropertyValues("otboo.security.user-session.expiration-policy=unknown").run(context -> {
        // then
        assertThat(context).doesNotHaveBean(UserSessionExpirationPolicy.class);
      });
    }
  }
}
