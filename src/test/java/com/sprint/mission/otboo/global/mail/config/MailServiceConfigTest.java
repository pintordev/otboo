package com.sprint.mission.otboo.global.mail.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.global.mail.service.MailService;
import com.sprint.mission.otboo.global.mail.service.impl.GoogleSmtpMailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

@DisplayName("MailServiceConfig")
class MailServiceConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(MailServiceConfig.class)
      .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
      .withBean(TemplateEngine.class, () -> mock(TemplateEngine.class));

  @Test
  @DisplayName("impl 값이 없으면 GoogleSmtpMailService가 등록된다")
  void impl_값이_없으면_GoogleSmtpMailService가_등록된다() {
    // when & then
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(MailService.class);
      assertThat(context.getBean(MailService.class))
          .isInstanceOf(GoogleSmtpMailService.class);
    });
  }

  @Test
  @DisplayName("impl 값이 google이면 GoogleSmtpMailService가 등록된다")
  void impl_값이_google이면_GoogleSmtpMailService가_등록된다() {
    // when & then
    contextRunner.withPropertyValues("otboo.mail.impl=google")
        .run(context -> assertThat(context.getBean(MailService.class))
            .isInstanceOf(GoogleSmtpMailService.class));
  }
}
