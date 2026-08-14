package com.sprint.mission.otboo.domain.weathernotification.sse.event;

import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSignedInEventListener")
class UserSignedInEventListenerTest {

  @InjectMocks
  private UserSignedInEventListener userSignedInEventListener;

  @Mock
  private SseService sseService;

  @Nested
  @DisplayName("이벤트 처리 (on)")
  class On {

    @Test
    @DisplayName("이벤트를 받으면 해당 유저의 기존 SSE 연결을 종료한다")
    void 이벤트를_받으면_해당_유저의_기존_SSE_연결을_종료한다() {
      // given
      UUID userId = UUID.randomUUID();
      UserSignedInEvent event = new UserSignedInEvent(userId);

      // when
      userSignedInEventListener.on(event);

      // then
      verify(sseService).disconnect(userId);
    }
  }
}