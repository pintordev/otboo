package com.sprint.mission.otboo.global.mail.config;

import com.sprint.mission.otboo.global.mail.properties.MailProperties;
import com.sprint.mission.otboo.global.mail.service.MailService;
import com.sprint.mission.otboo.global.mail.service.impl.GoogleSmtpMailService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailServiceConfig {

  @Bean
  public MailService mailService(MailProperties mailProperties, JavaMailSender mailSender,
      TemplateEngine templateEngine) {
    return switch (mailProperties.impl()) {
      case GOOGLE -> new GoogleSmtpMailService(mailSender, templateEngine);
    };
  }
}
