package com.sprint.mission.otboo.domain.authuser.auth.event;

import com.sprint.mission.otboo.global.mail.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TempPasswordEventListener {

  private final MailService mailService;

  @Async("mailExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handler(TempPasswordRequestedEvent event) {
    mailService.sendTempPassword(event.email(), event.rawTempPassword());
  }
}
