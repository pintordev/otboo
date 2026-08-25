package com.sprint.mission.otboo.global.mail.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otboo.mail")
public record MailProperties(
    MailImplType impl,
    String from
) {

  public MailProperties {
    if (impl == null) {
      impl = MailImplType.GOOGLE;
    }
    if (from == null || from.isBlank()) {
      from = "no-reply@otboo.cc";
    }
  }
}
