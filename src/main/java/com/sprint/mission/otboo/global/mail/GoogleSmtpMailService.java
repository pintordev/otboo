package com.sprint.mission.otboo.global.mail;

import com.sprint.mission.otboo.global.mail.exception.MailSendErrorException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSmtpMailService implements MailService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;

  @Override
  public void sendTempPassword(String toEmail, String rawTempPassword) {
    try {
      Context context = new Context();
      context.setVariable("temporaryPassword", rawTempPassword);
      context.setVariable("expireMinutes", 3);
      String html = templateEngine.process("template/mail/temporary-password", context);

      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setTo(toEmail);
      helper.setSubject("[옷장을 부탁해] 임시 비밀번호 안내");
      helper.setText(html, true);

      mailSender.send(message);
    } catch (MessagingException | MailException e) {
      throw new MailSendErrorException(toEmail, e);
    }
  }
}
