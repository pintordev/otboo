package com.sprint.mission.otboo.domain.authuser.auth.event;

import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.global.mail.service.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TempPasswordEventListener")
class TempPasswordEventListenerTest {

  @InjectMocks
  private TempPasswordEventListener tempPasswordEventListener;

  @Mock
  private MailService mailService;

  @Nested
  @DisplayName("이벤트 처리 (handler)")
  class Handler {

    @Test
    @DisplayName("이벤트를 받으면 MailService로 임시 비밀번호 메일을 전송한다")
    void 이벤트를_받으면_MailService로_임시_비밀번호_메일을_전송한다() {
      // given
      TempPasswordRequestedEvent event =
          new TempPasswordRequestedEvent("hong@test.com", "temp-password!", 3);

      // when
      tempPasswordEventListener.handler(event);

      // then
      verify(mailService).sendTempPassword("hong@test.com", "temp-password!", 3);
    }
  }
}
