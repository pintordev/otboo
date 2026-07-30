package com.sprint.mission.otboo.global.mail;

public interface MailService {

  void sendTempPassword(String toEmail, String rawTempPassword);
}
