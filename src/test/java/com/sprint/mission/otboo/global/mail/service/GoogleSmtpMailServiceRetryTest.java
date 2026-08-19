package com.sprint.mission.otboo.global.mail.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.global.config.RetryConfig;
import com.sprint.mission.otboo.global.mail.exception.MailSendErrorException;
import com.sprint.mission.otboo.global.mail.service.impl.GoogleSmtpMailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@SpringJUnitConfig(classes = {
    RetryConfig.class,
    GoogleSmtpMailServiceRetryTest.TestConfig.class
})
@DisplayName("GoogleSmtpMailService 재시도")
class GoogleSmtpMailServiceRetryTest {

  @TestConfiguration
  static class TestConfig {

    @Bean
    JavaMailSender mailSender() {
      return mock(JavaMailSender.class);
    }

    @Bean
    TemplateEngine templateEngine() {
      return mock(TemplateEngine.class);
    }

    @Bean
    GoogleSmtpMailService googleSmtpMailService(JavaMailSender mailSender,
        TemplateEngine templateEngine) {
      return new GoogleSmtpMailService(mailSender, templateEngine);
    }
  }

  @Autowired
  private MailService mailService;

  @Autowired
  private JavaMailSender mailSender;

  @Autowired
  private TemplateEngine templateEngine;

  @BeforeEach
  void resetMocks() {
    Mockito.reset(mailSender, templateEngine);
  }

  private MimeMessage realMimeMessage() {
    return new MimeMessage(Session.getDefaultInstance(new Properties()));
  }

  @Nested
  @DisplayName("전송이 계속 실패하는 경우")
  class AlwaysFails {

    @Test
    @DisplayName("최대_3번_시도한_후_마지막_예외를_전파한다")
    void 최대_3번_시도한_후_마지막_예외를_전파한다() {
      // given
      given(mailSender.createMimeMessage()).willReturn(realMimeMessage());
      given(templateEngine.process(any(String.class), any(Context.class)))
          .willReturn("<html></html>");
      willThrow(new MailSendException("SMTP 일시 오류")).given(mailSender)
          .send(any(MimeMessage.class));

      // when & then
      assertThatThrownBy(
          () -> mailService.sendTempPassword("hong@test.com", "raw-temp-password", 3))
          .isInstanceOf(MailSendErrorException.class);
      verify(mailSender, times(3)).send(any(MimeMessage.class));
    }
  }

  @Nested
  @DisplayName("재시도 중 성공하는 경우")
  class SucceedsAfterRetry {

    @Test
    @DisplayName("두_번_실패한_후_세_번째_시도에서_성공하면_예외_없이_종료한다")
    void 두_번_실패한_후_세_번째_시도에서_성공하면_예외_없이_종료한다() {
      // given
      given(mailSender.createMimeMessage()).willReturn(realMimeMessage());
      given(templateEngine.process(any(String.class), any(Context.class)))
          .willReturn("<html></html>");
      willThrow(new MailSendException("SMTP 일시 오류"))
          .willThrow(new MailSendException("SMTP 일시 오류"))
          .willDoNothing()
          .given(mailSender).send(any(MimeMessage.class));

      // when & then
      mailService.sendTempPassword("hong@test.com", "raw-temp-password", 3);
      verify(mailSender, times(3)).send(any(MimeMessage.class));
    }
  }
}
