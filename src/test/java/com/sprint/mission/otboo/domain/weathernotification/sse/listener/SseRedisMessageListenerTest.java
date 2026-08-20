package com.sprint.mission.otboo.domain.weathernotification.sse.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

class SseRedisMessageListenerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SseService sseService = mock(SseService.class);
  private final SseRedisMessageListener listener =
      new SseRedisMessageListener(objectMapper, sseService);

  @Nested
  @DisplayName("메시지 수신")
  class OnMessage {

    @Test
    @DisplayName("역직렬화한 메시지를 SseService로 로컬 전달한다")
    void 역직렬화한_메시지를_SseService로_로컬_전달한다() throws Exception {
      // given
      SseMessage sseMessage = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");
      byte[] body = objectMapper.writeValueAsBytes(sseMessage);
      Message message = new DefaultMessage("sse:notifications".getBytes(), body);

      // when
      listener.onMessage(message, null);

      // then
      verify(sseService).deliverLocally(sseMessage);
    }
  }
}