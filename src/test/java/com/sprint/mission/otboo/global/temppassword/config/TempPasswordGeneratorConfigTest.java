package com.sprint.mission.otboo.global.temppassword.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.temppassword.generator.TempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.generator.impl.RandomTempPasswordGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("TempPasswordGeneratorConfig")
class TempPasswordGeneratorConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(TempPasswordGeneratorConfig.class)
      .withPropertyValues("otboo.temp-password.expiration=3m");

  @Test
  @DisplayName("generator 값이 없으면 RandomTempPasswordGenerator가 등록된다")
  void generator_값이_없으면_RandomTempPasswordGenerator가_등록된다() {
    // when & then
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(TempPasswordGenerator.class);
      assertThat(context.getBean(TempPasswordGenerator.class))
          .isInstanceOf(RandomTempPasswordGenerator.class);
    });
  }

  @Test
  @DisplayName("지원하지 않는 generator 값이면 컨텍스트 기동에 실패한다")
  void 지원하지_않는_generator_값이면_컨텍스트_기동에_실패한다() {
    // when & then
    contextRunner.withPropertyValues("otboo.temp-password.generator=unknown")
        .run(context -> assertThat(context).hasFailed());
  }
}
