package com.sprint.mission.otboo.global.mail.exception;

public class MailSendErrorException extends RuntimeException {

  public MailSendErrorException(String toEmail, Throwable cause) {
    super("메일 발송 실패: to=" + toEmail, cause);
  }
}
