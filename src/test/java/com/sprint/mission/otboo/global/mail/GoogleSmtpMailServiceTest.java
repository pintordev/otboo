package com.sprint.mission.otboo.global.mail;

import com.sprint.mission.otboo.global.mail.exception.MailSendErrorException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GoogleSmtpMailServiceTest {

  @InjectMocks
  GoogleSmtpMailService mailService;

  @Mock
  JavaMailSender mockMailSender;

  @Mock
  TemplateEngine mockTemplateEngine;

  private MimeMessage realMimeMessage() {
    return new MimeMessage(Session.getDefaultInstance(new Properties()));
  }

  @Nested
  @DisplayName("발송 성공")
  class SendSuccess {

    @Test
    @DisplayName("템플릿을 렌더링해 지정된 수신자/제목으로 HTML 메일을 발송한다")
    void sendTempPassword_success_sendsRenderedHtmlMail() throws Exception {
      // given
      MimeMessage mimeMessage = realMimeMessage();
      given(mockMailSender.createMimeMessage()).willReturn(mimeMessage);
      given(mockTemplateEngine.process(anyString(), any(Context.class)))
          .willReturn("<html>임시 비밀번호 안내</html>");

      // when
      mailService.sendTempPassword("hong@test.com", "raw-temp-password");

      // then
      verify(mockMailSender).send(mimeMessage);
      assertThat(mimeMessage.getAllRecipients()).extracting(Object::toString)
          .containsExactly("hong@test.com");
      assertThat(mimeMessage.getSubject()).isEqualTo("[옷장을 부탁해] 임시 비밀번호 안내");
    }

    @Test
    @DisplayName("Thymeleaf 컨텍스트에 원문 임시 비밀번호와 만료 시간(분)을 전달한다")
    void sendTempPassword_success_passesTempPasswordAndExpireMinutesToTemplate() {
      // given
      given(mockMailSender.createMimeMessage()).willReturn(realMimeMessage());
      given(mockTemplateEngine.process(anyString(), any(Context.class)))
          .willReturn("<html></html>");

      // when
      mailService.sendTempPassword("hong@test.com", "raw-temp-password");

      // then
      ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
      verify(mockTemplateEngine).process(eq("template/mail/temporary-password"),
          contextCaptor.capture());
      Context usedContext = contextCaptor.getValue();
      assertThat(usedContext.getVariable("temporaryPassword")).isEqualTo("raw-temp-password");
      assertThat(usedContext.getVariable("expireMinutes")).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("발송 실패")
  class SendFailure {

    @Test
    @DisplayName("메일 전송 중 MailException이 발생하면 MailSendErrorException으로 감싸 던진다")
    void sendTempPassword_mailExceptionOnSend_throwsMailSendErrorException() {
      // given
      given(mockMailSender.createMimeMessage()).willReturn(realMimeMessage());
      given(mockTemplateEngine.process(anyString(), any(Context.class)))
          .willReturn("<html></html>");
      willThrow(new MailSendException("SMTP 인증 실패"))
          .given(mockMailSender).send(any(MimeMessage.class));

      // when & then
      assertThatThrownBy(() -> mailService.sendTempPassword("hong@test.com", "raw-temp-password"))
          .isInstanceOf(MailSendErrorException.class)
          .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("MailSendErrorException 메시지에는 수신자 이메일만 남고 원문 임시 비밀번호는 포함하지 않는다")
    void sendTempPassword_onFailure_exceptionMessageDoesNotLeakRawPassword() {
      // given
      given(mockMailSender.createMimeMessage()).willReturn(realMimeMessage());
      given(mockTemplateEngine.process(anyString(), any(Context.class)))
          .willReturn("<html></html>");
      willThrow(new MailSendException("SMTP 인증 실패"))
          .given(mockMailSender).send(any(MimeMessage.class));

      // when & then
      assertThatThrownBy(
          () -> mailService.sendTempPassword("hong@test.com", "super-secret-raw-password"))
          .hasMessageContaining("hong@test.com")
          .hasMessageNotContaining("super-secret-raw-password");
    }
  }
}
