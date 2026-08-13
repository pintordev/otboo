package com.sprint.mission.otboo.global.temppassword.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.registry.TempPasswordRegistry;
import com.sprint.mission.otboo.global.temppassword.registry.impl.TempPasswordRedisRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("TempPasswordRegistryConfig")
class TempPasswordRegistryConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TempPasswordRegistryConfig.class)
      .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
      .withBean(TempPasswordGenerator.class, () -> mock(TempPasswordGenerator.class))
      .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
      .withPropertyValues("otboo.temp-password.expiration=3m");

  @Test
  @DisplayName("impl 값이 없으면 TempPasswordRedisRegistry가 등록된다")
  void impl_값이_없으면_TempPasswordRedisRegistry가_등록된다() {
    // when & then
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(TempPasswordRegistry.class);
      assertThat(context.getBean(TempPasswordRegistry.class))
          .isInstanceOf(TempPasswordRedisRegistry.class);
    });
  }

  @Test
  @DisplayName("impl이 redis면 TempPasswordRedisRegistry가 등록된다")
  void impl이_redis면_TempPasswordRedisRegistry가_등록된다() {
    // when & then
    contextRunner.withPropertyValues("otboo.temp-password.impl=redis")
        .run(context -> assertThat(context.getBean(TempPasswordRegistry.class))
            .isInstanceOf(TempPasswordRedisRegistry.class));
  }

  @Test
  @DisplayName("지원하지 않는 impl 값이면 컨텍스트 기동에 실패한다")
  void 지원하지_않는_impl_값이면_컨텍스트_기동에_실패한다() {
    // when & then
    contextRunner.withPropertyValues("otboo.temp-password.impl=unknown")
        .run(context -> assertThat(context).hasFailed());
  }
}
