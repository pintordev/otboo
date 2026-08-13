package com.sprint.mission.otboo.global.mail.service;

public interface MailService {

  void sendTempPassword(String toEmail, String rawTempPassword, int expireMinutes);
}
