package com.sprint.mission.otboo.domain.authuser.auth.event;

import com.sprint.mission.otboo.global.mail.MailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TempPasswordEventListenerTest {

  @InjectMocks
  TempPasswordEventListener listener;

  @Mock
  MailService mockMailService;

  @Test
  @DisplayName("이벤트를 수신하면 이메일과 원문 임시 비밀번호로 메일 발송을 위임한다")
  void handler_onEvent_delegatesToMailService() {
    // given
    TempPasswordRequestedEvent event =
        new TempPasswordRequestedEvent("hong@test.com", "raw-temp-password");

    // when
    listener.handler(event);

    // then
    verify(mockMailService).sendTempPassword("hong@test.com", "raw-temp-password");
  }

  @Test
  @DisplayName("메일 발송이 실패하면 예외를 삼키지 않고 그대로 전파한다 (AsyncUncaughtExceptionHandler가 처리하도록)")
  void handler_mailServiceThrows_propagatesException() {
    // given
    TempPasswordRequestedEvent event =
        new TempPasswordRequestedEvent("hong@test.com", "raw-temp-password");
    willThrow(new RuntimeException("메일 발송 실패"))
        .given(mockMailService).sendTempPassword(eq("hong@test.com"), eq("raw-temp-password"));

    // when & then
    assertThatThrownBy(() -> listener.handler(event))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("메일 발송 실패");
  }
}
